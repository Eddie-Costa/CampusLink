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

@Repository
public class AdminUsuarioRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transacao;

    public AdminUsuarioRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transacao = new TransactionTemplate(transactionManager);
        // preserva a transacao independente que cada gravacao abria no dao
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

    public List<usuarioAdminDTO> listarUsuarios() throws SQLException {
        String sql = SQL_USUARIOS + " WHERE a.id IS NOT NULL OR p.id IS NOT NULL OR adm.id_usuario IS NOT NULL ORDER BY u.nome, u.id";
        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarUsuario(rs));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public usuarioAdminDTO buscarUsuarioPorId(Long idUsuario) throws SQLException {
        try {
            return consultarUsuarioPorId(idUsuario);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public usuarioAdminDTO buscarAdministradorPorEmail(String email) throws SQLException {
        try {
            return consultarAdministradorPorEmail(email);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<String> validarDadosEdicao(Long idUsuario, String perfil, String identificador, String email, String telefone) throws SQLException {
        List<String> erros = new ArrayList<>();
        try {
            if (emailJaExiste(idUsuario, email)) {
                erros.add("E-mail já cadastrado para outro usuário.");
            }
            if (telefoneJaExiste(idUsuario, telefone)) {
                erros.add("Telefone já cadastrado para outro usuário.");
            }
            if (identificadorJaExiste(idUsuario, perfil, identificador)) {
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
        if ("Aluno".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."ALUNOS"
                    SET rgm = ?
                    WHERE id_usuario = ?
                    """;
        } else if ("Professor".equalsIgnoreCase(usuario.getPerfil())) {
            sqlPerfil = """
                    UPDATE public."PROFESSORES"
                    SET matricula = ?
                    WHERE id_usuario = ?
                    """;
        } else {
            return false;
        }

        try {
            return transacao.execute(status -> {
                if (jdbcTemplate.update(sqlPerfil, usuario.getIdentificador(), usuario.getIdUsuario()) == 0) {
                    status.setRollbackOnly();
                    return false;
                }
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

        try {
            return jdbcTemplate.update(sql, status, idUsuario) > 0;
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean atualizarStatusUsuario(Long idUsuario, boolean status, String emailAdmin) throws SQLException {
        try {
            return transacao.execute(transacaoStatus -> {
                bloquearAdministradores();
                usuarioAdminDTO adminLogado = consultarAdministradorPorEmail(emailAdmin);
                validarAdministradorLogado(adminLogado);

                usuarioAdminDTO usuario = consultarUsuarioPorId(idUsuario);
                if (usuario == null) {
                    transacaoStatus.setRollbackOnly();
                    return false;
                }
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

    public boolean excluirAdministrador(Long idUsuario, String emailAdmin) throws SQLException {
        try {
            return transacao.execute(status -> {
                bloquearAdministradores();
                usuarioAdminDTO adminLogado = consultarAdministradorPorEmail(emailAdmin);
                validarAdministradorLogado(adminLogado);

                usuarioAdminDTO usuario = consultarUsuarioPorId(idUsuario);
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

                if (jdbcTemplate.update(sqlUsuario, idUsuario) == 0) {
                    throw new IllegalStateException("Esta conta também possui perfil de aluno ou professor. Utilize a desativação.");
                }
                return true;
            });
        } catch (DataAccessException | TransactionException e) {
            throw converterErro(e);
        }
    }

    private usuarioAdminDTO consultarUsuarioPorId(Long idUsuario) {
        String sql = SQL_USUARIOS + " WHERE u.id = ? AND (a.id IS NOT NULL OR p.id IS NOT NULL OR adm.id_usuario IS NOT NULL)";
        return jdbcTemplate.query(sql, rs -> rs.next() ? montarUsuario(rs) : null, idUsuario);
    }

    private usuarioAdminDTO consultarAdministradorPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String sql = SQL_USUARIOS + " WHERE LOWER(u.email) = ? AND adm.id_usuario IS NOT NULL";
        return jdbcTemplate.query(sql, rs -> rs.next() ? montarUsuario(rs) : null, normalizarEmail(email));
    }

    private void bloquearAdministradores() {
        // evita duas alteracoes ao mesmo tempo durante a conferencia dos administradores
        String sql = "LOCK TABLE public.\"ADMINISTRADOR\", public.\"USUARIOS\" IN SHARE ROW EXCLUSIVE MODE";
        jdbcTemplate.execute(sql);
    }

    private void validarAdministradorLogado(usuarioAdminDTO adminLogado) {
        if (adminLogado == null || !adminLogado.isStatus()) {
            throw new IllegalStateException("Sua conta de administrador não está disponível. Entre novamente.");
        }
    }

    private void validarAlteracaoAdministrador(usuarioAdminDTO usuario, usuarioAdminDTO adminLogado, boolean retirada) {
        if (usuario.isMaster() && !adminLogado.isMaster()) {
            throw new IllegalStateException("Somente outro administrador master pode alterar esta conta.");
        }
        if (retirada) {
            if (usuario.isStatus() && contarAdministradoresAtivos() <= 1) {
                throw new IllegalStateException("O sistema precisa manter pelo menos um administrador ativo.");
            }
            if (usuario.getIdUsuario().equals(adminLogado.getIdUsuario())) {
                throw new IllegalStateException("Você não pode excluir ou desativar sua própria conta de administrador.");
            }
        }
    }

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

    private boolean emailJaExiste(Long idUsuario, String email) {
        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE LOWER(email) = ?
                AND id <> ?
                """;
        return jdbcTemplate.query(sql, rs -> { return rs.next(); }, normalizarEmail(email), idUsuario);
    }

    private boolean telefoneJaExiste(Long idUsuario, String telefone) {
        String sql = """
                SELECT 1
                FROM public."USUARIOS"
                WHERE telefone = ?
                AND id <> ?
                """;
        return jdbcTemplate.query(sql, rs -> { return rs.next(); }, telefone, idUsuario);
    }

    private boolean identificadorJaExiste(Long idUsuario, String perfil, String identificador) {
        String sql;
        if ("Aluno".equalsIgnoreCase(perfil)) {
            sql = """
                    SELECT 1
                    FROM public."ALUNOS"
                    WHERE rgm = ?
                    AND id_usuario <> ?
                    """;
        } else if ("Professor".equalsIgnoreCase(perfil)) {
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

    private usuarioAdminDTO montarUsuario(ResultSet rs) throws SQLException {
        usuarioAdminDTO usuario = new usuarioAdminDTO();

        usuario.setIdUsuario(rs.getLong("id_usuario"));
        usuario.setNome(rs.getString("nome"));
        usuario.setEmail(rs.getString("email"));
        usuario.setTelefone(rs.getString("telefone"));
        usuario.setStatus(rs.getBoolean("status"));
        usuario.setMaster(rs.getBoolean("master"));

        Date dataNasc = rs.getDate("datanasc");

        if (dataNasc != null) {
            usuario.setDataNasc(dataNasc.toString());
        }

        Long idAluno = rs.getObject("id_aluno", Long.class);
        Long idProfessor = rs.getObject("id_professor", Long.class);
        Long idAdmin = rs.getObject("id_admin", Long.class);

        if (idAdmin != null) {
            usuario.setIdentificador(String.valueOf(usuario.getIdUsuario()));
            usuario.setPerfil("Administrador");

        } else if (idAluno != null) {
            usuario.setIdPerfil(idAluno);
            usuario.setIdentificador(rs.getString("rgm"));
            usuario.setPerfil("Aluno");

        } else if (idProfessor != null) {
            usuario.setIdPerfil(idProfessor);
            usuario.setIdentificador(rs.getString("matricula"));
            usuario.setPerfil("Professor");
        }

        return usuario;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // mantem o contrato de erro usado pelo controller atual
    private SQLException converterErro(RuntimeException erro) {
        Throwable causa = erro;
        while (causa != null) {
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
