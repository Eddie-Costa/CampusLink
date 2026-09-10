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

        String sql = "SELECT id FROM public.\"ALUNOS\" WHERE \"email\" = ?";
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
}
