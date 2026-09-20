package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.PrintWriter;
import java.io.StringWriter;

@Repository
public class alunoDAO {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger =
            LoggerFactory.getLogger(alunoDAO.class);

    @Autowired
    private DataSource dataSource;

    public loginAlunoDTO buscarPorEmailAluno(String email)
            throws SQLException {

        String sql = """
                SELECT "email", "senha"
                FROM public."USUARIOS"
                WHERE "email" = ?
                """;

        logger.debug("Consultando credenciais do aluno no banco.");
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", alunoDAO.class.getName(), "buscarPorEmailAluno", null,
                        "Consultando credenciais do aluno no banco.", null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    loginAlunoDTO usuario = new loginAlunoDTO();
                    usuario.setEmail(rs.getString("email"));
                    usuario.setSenha(rs.getString("senha"));
                    logger.debug("Credenciais do aluno encontradas.");
                    if (logger.isDebugEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "DEBUG", alunoDAO.class.getName(), "buscarPorEmailAluno", null,
                                    "Credenciais do aluno encontradas.", null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                    return usuario;
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao buscar credenciais do aluno no banco.", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", alunoDAO.class.getName(), "buscarPorEmailAluno", null,
                            "Erro ao buscar credenciais do aluno no banco.", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
        logger.debug("Nenhum aluno encontrado na consulta de credenciais.");
        if (logger.isDebugEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "DEBUG", alunoDAO.class.getName(), "buscarPorEmailAluno", null,
                        "Nenhum aluno encontrado na consulta de credenciais.", null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return null;
    }

    public String buscarPorIDAluno(String email) throws SQLException {

        String  id = "";

        String sql = "SELECT a.id FROM public.\"ALUNOS\" a JOIN public.\"USUARIOS\" u ON a.id_usuario = u.id WHERE u.email = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            id = rs.getString("id");
        }

        rs.close();
        stmt.close();
        conn.close();
        return id;
    }

    public void InsertAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {

        String sql = "INSERT INTO public.\"ALUNO_TURMA\" (\"id_aluno\", \"id_turma\") VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idAluno));
                stmt.setLong(2, Long.parseLong(idTurma));

                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhum professor_turma foi inserido.");
                }

            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    public void removeAluno_TurmaIntoBD(String idAluno, String idTurma) throws SQLException {

        String sql = "DELETE FROM public.\"ALUNO_TURMA\" WHERE \"id_aluno\" = ? AND \"id_turma\" = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idAluno));
                stmt.setLong(2, Long.parseLong(idTurma));

                stmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }
}
