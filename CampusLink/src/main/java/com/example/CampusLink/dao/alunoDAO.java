package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class alunoDAO {

    @Autowired
    private DataSource dataSource;

    public loginAlunoDTO buscarPorEmailAluno(String email)
            throws SQLException {

        String sql = """
                SELECT "email", "senha"
                FROM public."USUARIOS"
                WHERE "email" = ?
                """;

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

                    return usuario;
                }
            }
        }

        return null;
    }

    public String buscarPorIDAluno(String email)
            throws SQLException {

        String sql = """
                SELECT a."id"
                FROM public."ALUNOS" a
                INNER JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE u."email" = ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        return "";
    }
}