package com.example.CampusLink.dao;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class alunoDAO {

    @Autowired
    private DataSource dataSource;

    public loginAlunoDTO buscarPorEmailAluno(String email) throws SQLException {

        String sql = "SELECT * FROM public.\"USUARIOS\" WHERE \"email\" = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, email);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            loginAlunoDTO usuario = new loginAlunoDTO();
            usuario.setEmail(rs.getString("EMAIL"));
            usuario.setSenha(rs.getString("SENHA"));

            return usuario;
        }

        rs.close();
        stmt.close();
        conn.close();
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
