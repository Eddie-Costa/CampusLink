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

@Repository
public class alunoDAO {

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
                    return usuario;
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao buscar credenciais do aluno no banco.", e);
            throw e;
        }
        logger.debug("Nenhum aluno encontrado na consulta de credenciais.");
        return null;
    }

    public String buscarPorIDAluno(String email) throws SQLException {

        String sql = """
                SELECT a."id"
                FROM public."ALUNOS" a
                INNER JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE u."email" = ?
                """;

        logger.debug("Consultando ID do aluno no banco.");

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String idAluno = rs.getString("id");
                    logger.debug("ID do aluno encontrado. alunoId={}", idAluno);
                    return idAluno;
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao buscar o ID do aluno no banco.", e);
            throw e;
        }
        logger.warn("Nenhum ID de aluno foi encontrado.");

        return "";
    }
}