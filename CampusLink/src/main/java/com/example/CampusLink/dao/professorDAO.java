package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class professorDAO {

    @Autowired
    private DataSource dataSource;

    public loginProfessorDTO buscarPorEmailProfessor(String email) throws SQLException {

        String sql = "SELECT * FROM public.\"USUARIOS\" WHERE \"email\" = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginProfessorDTO usuario = new loginProfessorDTO();
            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();
        return null;
    }

    public String buscarPorIDProfessor(String email) throws SQLException {

        String  id = "";

        String sql = "SELECT p.id FROM public.\"PROFESSORES\" p\n" + "JOIN public.\"USUARIOS\" u ON p.id_usuario = u.id WHERE u.email = ?";
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

    public void InsertTurmasIntoBD(String nomeTurma, String idProfessor) throws SQLException {

        String sql = "INSERT INTO public.\"TURMAS\" (\"nome_turma\", \"id_professores\") VALUES (?, ARRAY[?::bigint])";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nomeTurma);
                stmt.setLong(2, Long.parseLong(idProfessor));

                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Nenhuma turma foi inserida.");
                }
            }

            conn.commit();
        } catch (SQLException e) {
            throw e;
        }
    }

    public void InsertProfessor_TurmaIntoBD(String idProfessor, String idTurma) throws SQLException {

        String sql = "INSERT INTO public.\"PROFESSOR_TURMA\" (\"id_professor\", \"id_turma\") VALUES (?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, Long.parseLong(idProfessor));
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
}
