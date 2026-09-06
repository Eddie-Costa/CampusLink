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
}
