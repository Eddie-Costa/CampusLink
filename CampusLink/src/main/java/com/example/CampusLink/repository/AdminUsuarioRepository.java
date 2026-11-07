// banco de usuarios administrativos centraliza as operacoes de gerenciamento de contas
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// persistencia administrativa de contas esta classe valida duplicidades e protege alteracoes de administradores
@Repository
public class AdminUsuarioRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;

    // preparar gravacao administrativa de usuarios este construtor recebe o banco e configura gravacoes independentes
    public AdminUsuarioRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = new TransactionTemplate(transactionManager);

        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static final String SQL_USUARIOS = """
            SELECT
                u.id AS id_usuario,
                a.id AS id_aluno,
                p.id AS id_professor,
                adm.id_usuario AS id_admin,
                a.rgm,
                p.matricula,
                u.nome,
                u.email,
                u.telefone,
                u.datanasc,
                u.status,
                COALESCE(adm.master, false) AS master
            FROM public."USUARIOS" u
            LEFT JOIN public."ALUNOS" a ON a.id_usuario = u.id
            LEFT JOIN public."PROFESSORES" p ON p.id_usuario = u.id
            LEFT JOIN public."ADMINISTRADOR" adm ON adm.id_usuario = u.id
            """;

    // listar contas no banco administrativo este metodo consulta usuarios com perfil e ordena por nome
    public List<usuarioAdminDTO> listarUsuarios() throws SQLException {
        String sql = SQL_USUARIOS + " WHERE a.id IS NOT NULL OR p.id IS NOT NULL OR adm.id_usuario IS NOT NULL ORDER BY u.nome, u.id";
        // consultar usuarios no banco monta a lista de contas e converte falhas da consulta
        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarUsuario(rs));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // buscar usuario administrativo por identificador este metodo consulta a conta e trata falhas do banco
    public usuarioAdminDTO buscarUsuarioPorId(Long idUsuario) throws SQLException {
        // buscar conta pelo codigo consulta o usuario e converte falhas do banco
        try {
            return consultarUsuarioPorId(idUsuario);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // buscar administrador por email este metodo consulta a conta administrativa e trata falhas do banco
    public usuarioAdminDTO buscarAdministradorPorEmail(String email) throws SQLException {
        // buscar administrador pelo email consulta a conta e converte falhas do banco
        try {
            return consultarAdministradorPorEmail(email);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // validar edicao sem duplicidade este metodo procura email telefone e identificador usados por outras contas
    public List<String> validarDadosEdicao(Long idUsuario, String perfil, String identificador, String email, String telefone) throws SQLException {
        List<String> erros = new ArrayList<>();
        // conferir duplicidades da edicao consulta conflitos de email telefone e identificador e trata falhas do banco
        try {
            // detectar email em uso inclui o aviso quando outra conta ja possui o email
            if (emailJaExiste(idUsuario, email)) {
                erros.add("E-mail já cadastrado para outro usuário.");
            }
            // detectar telefone em uso inclui o aviso quando outra conta ja possui o telefone
            if (telefoneJaExiste(idUsuario, telefone)) {
                erros.add("Telefone já cadastrado para outro usuário.");
            }
            // detectar identificador em uso escolhe o aviso quando o registro academico ja existe
            if (identificadorJaExiste(idUsuario, perfil, identificador)) {
                // distinguir registro academico informa rgm repetido para aluno ou matricula repetida para professor
                if ("Aluno".equalsIgnoreCase(perfil)) {
                    erros.add("RGM já cadastrado para outro aluno.");
                } else {
                    erros.add("Matrícula já cadastrada para outro professor.");
                }
            }
            return erros;
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // editar cadastro no banco administrativo este metodo grava dados pessoais e identificador juntos
    public boolean atualizarUsuario(usuarioAdminDTO usuario) throws SQLException {
        String sqlUsuario = """
                UPDATE public."USUARIOS"
                SET nome = ?,
                    email = ?,
                    telefone = ?,
                    datanasc = ?
                WHERE id = ?
                """;

        String sqlPerfil;
        // atualizar rgm escolhe a gravacao do identificador quando o perfil e aluno
        if ("Aluno".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."ALUNOS"
                    SET rgm = ?
                    WHERE id_usuario = ?
                    """;
        }
        // atualizar matricula escolhe a gravacao do professor e recusa os demais perfis
        else if ("Professor".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."PROFESSORES"
                    SET matricula = ?
                    WHERE id_usuario = ?
                    """;
        } else {
            return false;
        }

        // salvar perfil e conta grava os dados academicos e pessoais juntos e converte falhas
        try {
            return transacao.execute(status -> {
                // cancelar perfil nao alterado desfaz a operacao quando nenhum identificador academico foi atualizado
                if (jdbcTemplate.update(sqlPerfil, usuario.getIdentificador(), usuario.getIdUsuario()) == 0) {
                    status.setRollbackOnly();
                    return false;
                }
                // cancelar conta nao alterada desfaz a operacao quando nenhum dado pessoal foi atualizado
                if (jdbcTemplate.update(sqlUsuario, usuario.getNome(), normalizarEmail(usuario.getEmail()),
                        usuario.getTelefone(), Date.valueOf(usuario.getDataNasc()), usuario.getIdUsuario()) == 0) {
                    status.setRollbackOnly();
                    return false;
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    // alterar situacao de aluno ou professor este metodo atualiza a conta sem permitir administradores
    public boolean atualizarStatusUsuario(Long idUsuario, boolean status) throws SQLException {
        String sql = """
                UPDATE public."USUARIOS" u
                SET status = ?
                WHERE u.id = ?
                AND NOT EXISTS (
                    SELECT 1
                    FROM public."ADMINISTRADOR" adm
                    WHERE adm.id_usuario = u.id
                )
                AND (
                    EXISTS (
                        SELECT 1
                        FROM public."ALUNOS" a
                        WHERE a.id_usuario = u.id
                    )
                    OR
                    EXISTS (
                        SELECT 1
                        FROM public."PROFESSORES" p
                        WHERE p.id_usuario = u.id
                    )
                )
                """;

        // gravar situacao da conta atualiza o estado do usuario e converte falhas do banco
        try {
            return jdbcTemplate.update(sql, status, idUsuario) > 0;
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // alterar situacao com autorizacao este metodo confere o administrador e protege contas administrativas
    public boolean atualizarStatusUsuario(Long idUsuario, boolean status, String emailAdmin) throws SQLException {
        // alterar situacao com permissao confere administradores e salva a nova situacao em uma unica operacao
        try {
            return transacao.execute(transacaoStatus -> {
                bloquearAdministradores();
                usuarioAdminDTO adminLogado = consultarAdministradorPorEmail(emailAdmin);
                validarAdministradorLogado(adminLogado);

                usuarioAdminDTO usuario = consultarUsuarioPorId(idUsuario);
                // cancelar situacao sem usuario desfaz a operacao quando a conta nao existe
                if (usuario == null) {
                    transacaoStatus.setRollbackOnly();
                    return false;
                }
                // proteger alteracao administrativa aplica as restricoes extras quando a conta alterada e de administrador
                if ("Administrador".equals(usuario.getPerfil())) {
                    validarAlteracaoAdministrador(usuario, adminLogado, !status);
                }

                String sql = "UPDATE public.\"USUARIOS\" SET status = ? WHERE id = ?";
                return jdbcTemplate.update(sql, status, idUsuario) > 0;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    // delete administrador no banco este metodo confere as protecoes e exclui o perfil e a conta juntos
    public boolean excluirAdministrador(Long idUsuario, String emailAdmin) throws SQLException {
        // delete conta administrativa confere restricoes e remove os dados do administrador juntos
        try {
            return transacao.execute(status -> {
                bloquearAdministradores();
                usuarioAdminDTO adminLogado = consultarAdministradorPorEmail(emailAdmin);
                validarAdministradorLogado(adminLogado);

                usuarioAdminDTO usuario = consultarUsuarioPorId(idUsuario);
                // cancelar exclusao administrativa recusa contas ausentes ou de outro perfil
                if (usuario == null || !"Administrador".equals(usuario.getPerfil())) {
                    status.setRollbackOnly();
                    return false;
                }
                validarAlteracaoAdministrador(usuario, adminLogado, true);

                String sqlAdmin = "DELETE FROM public.\"ADMINISTRADOR\" WHERE id_usuario = ?";
                jdbcTemplate.update(sqlAdmin, idUsuario);

                String sqlUsuario = """
                        DELETE FROM public."USUARIOS" u
                        WHERE u.id = ?
                        AND NOT EXISTS (
                            SELECT 1 FROM public."ALUNOS" a WHERE a.id_usuario = u.id
                        )
                        AND NOT EXISTS (
                            SELECT 1 FROM public."PROFESSORES" p WHERE p.id_usuario = u.id
                        )
                        """;

                // preservar conta com outro perfil impede excluir usuarios que tambem sao alunos ou professores
                if (jdbcTemplate.update(sqlUsuario, idUsuario) == 0) {
                    throw new IllegalStateException("Esta conta também possui perfil de aluno ou professor. Utilize a desativação.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    // consultar usuario com perfil este metodo busca uma conta pelo identificador e monta seus dados
    private usuarioAdminDTO consultarUsuarioPorId(Long idUsuario) {
        String sql = SQL_USUARIOS + " WHERE u.id = ? AND (a.id IS NOT NULL OR p.id IS NOT NULL OR adm.id_usuario IS NOT NULL)";
        return jdbcTemplate.query(sql, rs -> rs.next() ? montarUsuario(rs) : null, idUsuario);
    }

    // consultar administrador normalizando email este metodo encontra o perfil administrativo do endereco informado
    private usuarioAdminDTO consultarAdministradorPorEmail(String email) {
        // ignorar email administrativo vazio encerra a consulta quando nao existe email preenchido
        if (email == null || email.isBlank()) {
            return null;
        }
        String sql = SQL_USUARIOS + " WHERE LOWER(u.email) = ? AND adm.id_usuario IS NOT NULL";
        return jdbcTemplate.query(sql, rs -> rs.next() ? montarUsuario(rs) : null, normalizarEmail(email));
    }

    // impedir alteracoes administrativas simultaneas este metodo reserva as tabelas durante a conferencia das contas
    private void bloquearAdministradores() {

        String sql = "LOCK TABLE public.\"ADMINISTRADOR\", public.\"USUARIOS\" IN SHARE ROW EXCLUSIVE MODE";
        jdbcTemplate.execute(sql);
    }

    // conferir administrador ativo este metodo recusa uma conta ausente ou desativada
    private void validarAdministradorLogado(usuarioAdminDTO adminLogado) {
        // exigir administrador ativo impede a operacao quando a conta responsavel esta ausente ou desativada
        if (adminLogado == null || !adminLogado.isStatus()) {
            throw new IllegalStateException("Sua conta de administrador não está disponível. Entre novamente.");
        }
    }

    // proteger contas administrativas este metodo impede retirar o ultimo administrador e alterar contas sem permissao
    private void validarAlteracaoAdministrador(usuarioAdminDTO usuario, usuarioAdminDTO adminLogado, boolean retirada) {
        // proteger administrador principal exige outro administrador principal para alterar essa conta
        if (usuario.isMaster() && !adminLogado.isMaster()) {
            throw new IllegalStateException("Somente outro administrador master pode alterar esta conta.");
        }
        // validar retirada administrativa confere a continuidade do acesso antes de excluir ou desativar
        if (retirada) {
            // preservar ultimo administrador impede retirar a unica conta administrativa ativa
            if (usuario.isStatus() && contarAdministradoresAtivos() <= 1) {
                throw new IllegalStateException("O sistema precisa manter pelo menos um administrador ativo.");
            }
            // impedir retirada da propria conta recusa excluir ou desativar o administrador conectado
            if (usuario.getIdUsuario().equals(adminLogado.getIdUsuario())) {
                throw new IllegalStateException("Você não pode excluir ou desativar sua própria conta de administrador.");
            }
        }
    }

    // contar administradores ativos este metodo consulta quantas contas administrativas continuam habilitadas
    private int contarAdministradoresAtivos() {
        String sql = """
                SELECT COUNT(*)
                FROM public."USUARIOS" u
                WHERE u.status = true
                AND EXISTS (
                    SELECT 1
                    FROM public."ADMINISTRADOR" adm
                    WHERE adm.id_usuario = u.id
                )
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    // buscar email repetido na edicao este metodo procura o endereco em outra conta
    private boolean emailJaExiste(Long idUsuario, String email) {
        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE LOWER(email) = ?
                AND id <> ?
                """;
        return jdbcTemplate.query(sql, rs -> { return rs.next(); }, normalizarEmail(email), idUsuario);
    }

    // buscar telefone repetido na edicao este metodo procura o numero em outra conta
    private boolean telefoneJaExiste(Long idUsuario, String telefone) {
        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE telefone = ?
                AND id <> ?
                """;
        return jdbcTemplate.query(sql, rs -> { return rs.next(); }, telefone, idUsuario);
    }

    // buscar identificador repetido na edicao este metodo procura rgm ou matricula em outro usuario
    private boolean identificadorJaExiste(Long idUsuario, String perfil, String identificador) {
        String sql;
        // consultar rgm duplicado escolhe a busca nos alunos quando o perfil informado e aluno
        if ("Aluno".equalsIgnoreCase(perfil)) {
            sql = """
                    SELECT 1
                    FROM public."ALUNOS"
                    WHERE rgm = ?
                    AND id_usuario <> ?
                    """;
        }
        // consultar matricula duplicada busca outros professores ou recusa perfis nao reconhecidos
        else if ("Professor".equalsIgnoreCase(perfil)) {
            sql = """
                    SELECT 1
                    FROM public."PROFESSORES"
                    WHERE matricula = ?
                    AND id_usuario <> ?
                    """;
        } else {
            return true;
        }
        return jdbcTemplate.query(sql, rs -> { return rs.next(); }, identificador, idUsuario);
    }

    // montar conta administrativa este metodo organiza os dados consultados e identifica o perfil da conta
    private usuarioAdminDTO montarUsuario(ResultSet rs) throws SQLException {
        usuarioAdminDTO usuario = new usuarioAdminDTO();

        usuario.setIdUsuario(rs.getLong("id_usuario"));
        usuario.setNome(rs.getString("nome"));
        usuario.setEmail(rs.getString("email"));
        usuario.setTelefone(rs.getString("telefone"));
        usuario.setStatus(rs.getBoolean("status"));
        usuario.setMaster(rs.getBoolean("master"));

        Date dataNasc = rs.getDate("datanasc");

        // preencher nascimento da conta usa a data apenas quando ela existe no resultado
        if (dataNasc != null) {
            usuario.setDataNasc(dataNasc.toString());
        }

        Long idAluno = rs.getObject("id_aluno", Long.class);
        Long idProfessor = rs.getObject("id_professor", Long.class);
        Long idAdmin = rs.getObject("id_admin", Long.class);

        // montar perfil administrativo identifica a conta como administrador quando encontra esse vinculo
        if (idAdmin != null) {
            usuario.setIdentificador(String.valueOf(usuario.getIdUsuario()));
            usuario.setPerfil("Administrador");

        }
        // montar perfil de aluno inclui codigo academico e rgm quando encontra o cadastro de aluno
        else if (idAluno != null) {
            usuario.setIdPerfil(idAluno);
            usuario.setIdentificador(rs.getString("rgm"));
            usuario.setPerfil("Aluno");

        }
        // montar perfil de professor inclui codigo academico e matricula quando encontra o cadastro de professor
        else if (idProfessor != null) {
            usuario.setIdPerfil(idProfessor);
            usuario.setIdentificador(rs.getString("matricula"));
            usuario.setPerfil("Professor");
        }

        return usuario;
    }

    // normalizar email administrativo este metodo retira espacos das pontas e usa letras minusculas
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


    // tratar erro do banco de usuarios este metodo recupera a falha original ou cria uma falha de consulta
    private SQLException converterErro(RuntimeException erro) {
        Throwable causa = erro;
        while (causa != null) {
            // recuperar falha do banco devolve a causa original quando ela e uma falha de consulta
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
