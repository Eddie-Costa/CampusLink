package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.SupabaseStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.slf4j.helpers.MessageFormatter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class usuarioDAO {

    private static final Logger logger = LoggerFactory.getLogger(usuarioDAO.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SupabaseStorageService storage;

    public DadosUsuarioDTO consultarDadosPessoais(String email) throws SQLException {
        String sql = """
                SELECT u.id, u.nome, u.email, u.telefone, u.datanasc, u.perfil, u.created_at,
                       COALESCE(a.rgm, p.matricula, adm.matricula) AS identificador
                FROM public."USUARIOS" u
                LEFT JOIN public."ALUNOS" a ON a.id_usuario = u.id
                LEFT JOIN public."PROFESSORES" p ON p.id_usuario = u.id
                LEFT JOIN public."ADMINISTRADOR" adm ON adm.id_usuario = u.id
                WHERE LOWER(u.email) = ? AND u.status = true
                """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizarEmail(email));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DadosUsuarioDTO dadosUsuario = new DadosUsuarioDTO();
                    dadosUsuario.setId(rs.getLong("id"));
                    dadosUsuario.setNome(rs.getString("nome"));
                    dadosUsuario.setEmail(rs.getString("email"));
                    dadosUsuario.setTelefone(rs.getString("telefone"));
                    dadosUsuario.setDataNascimento(rs.getDate("datanasc").toLocalDate());
                    dadosUsuario.setPerfil(rs.getString("perfil"));
                    dadosUsuario.setIdentificador(rs.getString("identificador"));
                    dadosUsuario.setDataCadastro(rs.getDate("created_at").toLocalDate());
                    return dadosUsuario;
                }

                return null;
            }
        }
    }

    public boolean possuiVinculosParaExclusao(long idUsuario) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return possuiVinculosParaExclusao(conn, idUsuario);
        }
    }

    private boolean possuiVinculosParaExclusao(Connection conn, long idUsuario) throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM public."PROFESSORES" p WHERE p.id_usuario = ? AND (
                        EXISTS (SELECT 1 FROM public."TURMAS" t WHERE t.id_proprietario = p.id)
                        OR EXISTS (SELECT 1 FROM public."CONTEUDOS" c WHERE c.id_professor = p.id AND c.status <> 'removido')
                        OR EXISTS (SELECT 1 FROM public."EVENTOS" e WHERE e.id_professor = p.id)
                    )
                )
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, idUsuario);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    public void excluirDadosPessoais(long idUsuario, String email) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id FROM public.\"USUARIOS\" WHERE id = ? AND LOWER(email) = ? FOR UPDATE")) {
                    stmt.setLong(1, idUsuario);
                    stmt.setString(2, normalizarEmail(email));
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Conta não encontrada.");
                        }
                    }
                }
                // Repete a checagem na confirmação, pois vínculos podem ter sido criados após o envio do código.
                if (possuiVinculosParaExclusao(conn, idUsuario)) {
                    throw new IllegalStateException("Remova ou transfira suas turmas, materiais e eventos antes de excluir a conta.");
                }

                // "Remover material" oculta o conteúdo no CampusLink. Na exclusão da conta,
                // elimina também esses registros e os arquivos que já foram retirados de circulação.
                excluirMateriaisRemovidos(conn, idUsuario);

                executarExclusao(conn, """
                        DELETE FROM public."DENUNCIAS"
                        WHERE id_aluno IN (SELECT id FROM public."ALUNOS" WHERE id_usuario = ?)
                           OR id_professor IN (SELECT id FROM public."PROFESSORES" WHERE id_usuario = ?)
                        """, idUsuario, idUsuario);
                executarExclusao(conn, """
                        DELETE FROM public."DISPONIBILIDADES"
                        WHERE id_aluno IN (SELECT id FROM public."ALUNOS" WHERE id_usuario = ?)
                        """, idUsuario);
                executarExclusao(conn, """
                        DELETE FROM public."ALUNO_TURMA"
                        WHERE id_aluno IN (SELECT id FROM public."ALUNOS" WHERE id_usuario = ?)
                        """, idUsuario);
                executarExclusao(conn, """
                        DELETE FROM public."PROFESSOR_TURMA"
                        WHERE id_professor IN (SELECT id FROM public."PROFESSORES" WHERE id_usuario = ?)
                        """, idUsuario);
                executarExclusao(conn, """
                        UPDATE public."TURMAS" t SET id_alunos = array_remove(t.id_alunos, a.id)
                        FROM public."ALUNOS" a WHERE a.id_usuario = ? AND a.id = ANY(t.id_alunos)
                        """, idUsuario);
                executarExclusao(conn, """
                        UPDATE public."TURMAS" t SET id_professores = array_remove(t.id_professores, p.id)
                        FROM public."PROFESSORES" p WHERE p.id_usuario = ? AND p.id = ANY(t.id_professores)
                        """, idUsuario);
                executarExclusao(conn, "DELETE FROM public.\"ALUNOS\" WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, "DELETE FROM public.\"PROFESSORES\" WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, "DELETE FROM public.\"ADMINISTRADOR\" WHERE id_usuario = ?", idUsuario);
                executarExclusao(conn, "DELETE FROM public.\"USUARIOS\" WHERE id = ?", idUsuario);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    private void executarExclusao(Connection conn, String sql, long... ids) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                stmt.setLong(i + 1, ids[i]);
            }
            stmt.executeUpdate();
        }
    }

    private void excluirMateriaisRemovidos(Connection conn, long idUsuario) throws SQLException {
        String conteudos = """
                SELECT c.id FROM public."CONTEUDOS" c
                JOIN public."PROFESSORES" p ON p.id = c.id_professor
                WHERE p.id_usuario = ? AND c.status = 'removido' FOR UPDATE OF c
                """;
        try (PreparedStatement stmt = conn.prepareStatement(conteudos)) {
            stmt.setLong(1, idUsuario);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long idConteudo = rs.getLong("id");
                    try (PreparedStatement arquivos = conn.prepareStatement(
                            "SELECT bucket_id, storage_path FROM public.\"ARQUIVOS\" WHERE id_conteudo = ?")) {
                        arquivos.setLong(1, idConteudo);
                        try (ResultSet files = arquivos.executeQuery()) {
                            while (files.next()) {
                                storage.excluir(files.getString("bucket_id"), files.getString("storage_path"));
                            }
                        }
                    }
                    executarExclusao(conn, "DELETE FROM public.\"ARQUIVOS\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"EVENTO_CONTEUDO\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"DENUNCIAS\" WHERE id_conteudo = ?", idConteudo);
                    executarExclusao(conn, "DELETE FROM public.\"CONTEUDOS\" WHERE id = ?", idConteudo);
                }
            }
        }
    }

    public void InsertCadastroUsuarioIntoBD(String Tipo, String IDENTIFICADOR, String NOME, String EMAIL, String TELEFONE, String DATANASC, String SENHA) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String tabelaEspecifica;
        String colunaIdentificador;

        if (Tipo.equalsIgnoreCase("Aluno")) {
            tabelaEspecifica = "ALUNOS";
            colunaIdentificador = "rgm";
        } else if (Tipo.equalsIgnoreCase("Professor")) {
            tabelaEspecifica = "PROFESSORES";
            colunaIdentificador = "matricula";
        } else {
            throw new IllegalArgumentException("Tipo de usuário inválido: " + Tipo);
        }

        String sqlUsuario = "INSERT INTO public.\"USUARIOS\" (\"nome\", \"email\", \"telefone\", \"datanasc\", \"senha\", \"perfil\", \"status\") "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        String sqlEspecifico = "INSERT INTO public.\"" + tabelaEspecifica + "\" "
                + "(\"id_usuario\", \"" + colunaIdentificador + "\") VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sqlUsuario, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, NOME);
                stmt.setString(2, emailNormalizado);
                stmt.setString(3, TELEFONE);
                stmt.setDate(4, java.sql.Date.valueOf(java.time.LocalDate.parse(DATANASC)));
                stmt.setString(5, SENHA);
                stmt.setString(6, tabelaEspecifica);
                stmt.setBoolean(7, true);

                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhum usuário foi inserido.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new SQLException("Não foi possível obter o id do usuário inserido.");
                    }

                    long idUsuario = generatedKeys.getLong(1);

                    try (PreparedStatement stmt2 = conn.prepareStatement(sqlEspecifico)) {
                        stmt2.setLong(1, idUsuario);
                        stmt2.setString(2, IDENTIFICADOR);
                        stmt2.executeUpdate();
                    }
                }
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    public void InserirLogsNoBD(UUID sessaoLogId, String nivel, String classe, String operacao,
                                UUID operacaoId, String mensagem, String detalhes, String excecao) throws SQLException {

        if (sessaoLogId == null && MDC.get("sessaoLogId") != null) {
            sessaoLogId = UUID.fromString(MDC.get("sessaoLogId"));
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                boolean encerrada = sessaoLogId != null && bloquearSessaoLog(conn, sessaoLogId);

                inserirEventoLog(conn, sessaoLogId, nivel, classe, operacao, operacaoId, mensagem, detalhes, excecao);

                if (encerrada) {
                    atualizarHistoricoSessao(conn, sessaoLogId);
                }

                conn.commit();

            } catch (SQLException | RuntimeException e) {
                desfazerTransacaoLog(conn, e);
                throw e;
            }
        }
    }

    public void finalizarSessaoLog(UUID sessaoLogId, Instant inicio, Instant fim, String email) throws SQLException {
        String sql = """
                UPDATE public."LOGS_SESSOES"
                SET usuario_id = COALESCE(usuario_id,
                        (SELECT id FROM public."USUARIOS" WHERE LOWER(email) = ?)),
                    inicio = ?, fim = ?, status = 'ENCERRADA'
                WHERE sessao_log_id = ?
                """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (!bloquearSessaoLog(conn, sessaoLogId)) {
                    inserirEventoLog(
                            conn,
                            sessaoLogId,
                            "INFO",
                            "SessaoLogListener",
                            "sessionDestroyed",
                            null,
                            "Sessao invalidada. Historico consolidado.",
                            null,
                            null
                    );

                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, email == null ? null : normalizarEmail(email));
                        stmt.setTimestamp(2, Timestamp.from(inicio));
                        stmt.setTimestamp(3, Timestamp.from(fim));
                        stmt.setObject(4, sessaoLogId);
                        stmt.executeUpdate();
                    }
                }

                atualizarHistoricoSessao(conn, sessaoLogId);
                conn.commit();

            } catch (SQLException | RuntimeException e) {
                desfazerTransacaoLog(conn, e);
                throw e;
            }
        }
    }

    private boolean bloquearSessaoLog(Connection conn, UUID sessaoLogId) throws SQLException {
        String sql = "INSERT INTO public.\"LOGS_SESSOES\" (sessao_log_id) VALUES (?) "
                + "ON CONFLICT (sessao_log_id) DO NOTHING";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT fim FROM public.\"LOGS_SESSOES\" WHERE sessao_log_id = ? FOR UPDATE")) {

            stmt.setObject(1, sessaoLogId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Sessao de log nao encontrada.");
                }

                return rs.getTimestamp("fim") != null;
            }
        }
    }

    private void inserirEventoLog(Connection conn, UUID sessaoLogId, String nivel, String classe, String operacao,
                                  UUID operacaoId, String mensagem, String detalhes, String excecao) throws SQLException {

        String sql = "INSERT INTO public.\"LOGS_EVENTOS\" (sessao_log_id, nivel, classe, operacao, operacao_id, mensagem, detalhes, excecao) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.setString(2, nivel);
            stmt.setString(3, classe);
            stmt.setString(4, operacao);
            stmt.setObject(5, operacaoId);
            stmt.setString(6, mensagem);
            stmt.setString(7, detalhes);
            stmt.setString(8, excecao);

            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Nenhum log foi inserido.");
            }
        }
    }

    private void atualizarHistoricoSessao(Connection conn, UUID sessaoLogId) throws SQLException {
        String sql = """
            UPDATE public."LOGS_SESSOES" s SET log_completo = COALESCE(( SELECT string_agg(concat_ws(' ', e.data_hora::text, '[' || e.nivel || ']', e.classe,
            'eventoId=' || e.id, 'operacao=' || e.operacao, 'operacaoId=' || e.operacao_id, e.mensagem, E'\\nDetalhes: ' || e.detalhes::text, E'\\nExcecao: ' || e.excecao), E'\\n' ORDER BY e.data_hora, e.id)
            FROM public."LOGS_EVENTOS" e WHERE e.sessao_log_id = s.sessao_log_id), ''), historico_gerado_em = clock_timestamp() WHERE s.sessao_log_id = ?
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, sessaoLogId);
            stmt.executeUpdate();
        }
    }

    private void desfazerTransacaoLog(Connection conn, Exception causa) {
        try {
            conn.rollback();
        } catch (SQLException erroRollback) {
            causa.addSuppressed(erroRollback);
        }
    }

    public List<String> validarDadosDuplicados(String tipoUsuario, String identificador, String email, String telefone) {
        List<String> erros = new ArrayList<>();

        if (verificarExistente(tipoUsuario, "identificador", identificador)) {
            erros.add("Identificador já cadastrado");
        }

        if (verificarExistente(tipoUsuario, "email", email)) {
            erros.add("Email já cadastrado");
        }

        if (verificarExistente(tipoUsuario, "telefone", telefone)) {
            erros.add("Telefone já cadastrado");
        }

        return erros;
    }

    private boolean verificarExistente(String tipoUsuario, String tipoDado, String dado) {
        boolean existe = false;
        String sql = "";

        switch (tipoDado) {
            case "identificador":
                String tabela = tipoUsuario.equalsIgnoreCase("Aluno") ? "ALUNOS" : "PROFESSORES";
                String coluna = tipoUsuario.equalsIgnoreCase("Aluno") ? "rgm" : "matricula";

                sql = "SELECT 1 FROM public.\"USUARIOS\" u JOIN public.\"" + tabela + "\" p ON p.\"id_usuario\" = u.\"id\" "
                        + "WHERE p.\"" + coluna + "\" = ?";
                break;

            case "email":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE LOWER(\"email\") = ?";
                break;

            case "telefone":
                sql = "SELECT 1 FROM public.\"USUARIOS\" WHERE \"telefone\" = ?";
                break;

            default:
                throw new IllegalArgumentException("Tipo de dado inválido: " + tipoDado);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tipoDado.equals("email") ? normalizarEmail(dado) : dado);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                existe = true;
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return existe;
    }

    public String QueryLoginUsuario(String tipoUsuario, String EMAIL) throws SQLException {
        String emailNormalizado = normalizarEmail(EMAIL);
        String resultado = "";
        String sql = "";

        Connection conn = dataSource.getConnection();

        if (tipoUsuario.equalsIgnoreCase("Aluno")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        } else if (tipoUsuario.equalsIgnoreCase("Professor")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        } else if (tipoUsuario.equalsIgnoreCase("Administrador") || tipoUsuario.equalsIgnoreCase("Admin")) {
            sql = "SELECT u.\"senha\" FROM public.\"USUARIOS\" u "
                    + "JOIN public.\"ADMINISTRADOR\" a ON a.\"id_usuario\" = u.\"id\" "
                    + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";
        }

        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, emailNormalizado);

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            resultado = rs.getString("SENHA");
        }

        rs.close();
        stmt.close();
        conn.close();

        return resultado;
    }

    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"ALUNOS\" a ON a.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginAlunoDTO usuario = new loginAlunoDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    public loginProfessorDTO buscarPorEmailProfessor(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"PROFESSORES\" p ON p.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginProfessorDTO usuario = new loginProfessorDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    public loginAdminDTO buscarPorEmailAdmin(String email) throws SQLException {
        String sql = "SELECT u.\"email\", u.\"senha\" FROM public.\"USUARIOS\" u "
                + "JOIN public.\"ADMINISTRADOR\" a ON a.\"id_usuario\" = u.\"id\" "
                + "WHERE LOWER(u.\"email\") = ? AND u.\"status\" = true";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginAdminDTO usuario = new loginAdminDTO();

            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            rs.close();
            stmt.close();
            conn.close();

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    public String buscarPorTipoUsuario(String email) throws SQLException {
        String sql = "SELECT u.\"perfil\" FROM public.\"USUARIOS\" u WHERE LOWER(u.\"email\") = ?";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, normalizarEmail(email));

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            String perfil = rs.getString("perfil");

            rs.close();
            stmt.close();
            conn.close();

            return perfil;
        }

        rs.close();
        stmt.close();
        conn.close();

        return null;
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void UpdateSenhaUsuario(String SENHA, String EMAIL) throws SQLException {
        Connection conn = dataSource.getConnection();

        String sql = "UPDATE public.\"USUARIOS\" SET \"senha\" = ? WHERE \"email\" = ?";

        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, SENHA);
        stmt.setString(2, EMAIL);

        logger.debug("Atualizando senha do usuário no banco.");

        if (logger.isDebugEnabled()) {
            try {
                InserirLogsNoBD(
                        null,
                        "DEBUG",
                        usuarioDAO.class.getName(),
                        "UpdateSenhaUsuario",
                        null,
                        "Atualizando senha do usuário no banco.",
                        null,
                        null
                );

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        int linhas = stmt.executeUpdate();

        logger.debug("Linhas afetadas: {}", linhas);

        if (logger.isDebugEnabled()) {
            try {
                InserirLogsNoBD(
                        null,
                        "DEBUG",
                        usuarioDAO.class.getName(),
                        "UpdateSenhaUsuario",
                        null,
                        MessageFormatter.arrayFormat(
                                "Linhas afetadas: {}",
                                new Object[]{linhas}
                        ).getMessage(),
                        null,
                        null
                );

            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        stmt.close();
        conn.close();
    }
}