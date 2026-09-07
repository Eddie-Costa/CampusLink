package com.example.CampusLink.dao;

import com.example.CampusLink.dto.TurmaDTO;
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
public class turmaDAO {

    @Autowired
    private DataSource dataSource;

    public String buscarPorIDTurma(String idProfessor) throws SQLException {

        String id = "";
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores)";

        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setLong(1, Long.parseLong(idProfessor));
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            id = rs.getString("id");
        }

        rs.close();
        stmt.close();
        conn.close();
        return id;
    }

    public String buscarUltimaTurmaPorProfessor(String idProfessor) throws SQLException {
        String sql = "SELECT t.id FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores) ORDER BY t.id DESC LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(idProfessor));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        }

        return null;
    }

    public List<TurmaDTO> buscarTurmasDoUsuario(String tipo, String id) throws SQLException {

        String sql;

        if (tipo.equalsIgnoreCase("aluno")) {
            sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_alunos)";
        } else if (tipo.equalsIgnoreCase("professor")) {
            sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE ? = ANY(t.id_professores)";
        } else {
            return List.of();
        }

        List<TurmaDTO> turmas = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(id));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TurmaDTO turma = new TurmaDTO();
                    turma.setId(rs.getLong("id"));
                    turma.setNomeTurma(rs.getString("nome_turma"));
                    turma.setDescricao(rs.getString("descricao"));
                    turmas.add(turma);
                }
            }
        }

        return turmas;
    }

    public TurmaDTO buscarTurmaPorId(String id) throws SQLException {
        String sql = "SELECT t.id, t.nome_turma, t.descricao FROM public.\"TURMAS\" t WHERE t.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, Long.parseLong(id));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TurmaDTO turma = new TurmaDTO();
                    turma.setId(rs.getLong("id"));
                    turma.setNomeTurma(rs.getString("nome_turma"));
                    turma.setDescricao(rs.getString("descricao"));
                    return turma;
                }
            }
        }

        return null;
    }
}
