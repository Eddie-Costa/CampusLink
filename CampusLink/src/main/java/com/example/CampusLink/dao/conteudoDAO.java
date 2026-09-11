package com.example.CampusLink.dao;

import com.example.CampusLink.dto.ConteudoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class conteudoDAO {

    @Autowired
    private DataSource dataSource;

    public void inserir(ConteudoDTO conteudo) throws SQLException {

        String sql = """
                INSERT INTO public."CONTEUDOS"
                (
                    "id_turma",
                    "id_professor",
                    "titulo",
                    "descricao",
                    "tipo",
                    "url",
                    "duracao_estimada",
                    "prioridade"
                )
                VALUES (ARRAY[?::bigint], ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, created_at
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, conteudo.getIdTurma());
            stmt.setLong(2, conteudo.getIdProfessor());
            stmt.setString(3, conteudo.getTitulo());
            String descricao = conteudo.getDescricao();
            stmt.setString(4, descricao == null ? "" : descricao);
            stmt.setString(5, conteudo.getTipo());
            stmt.setString(6, conteudo.getUrl());
            stmt.setString(7, conteudo.getDuracaoEstimada());
            stmt.setString(8, conteudo.getPrioridade());

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    conteudo.setId(rs.getLong("id"));

                    Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt != null) {
                        conteudo.setCreatedAt(
                                createdAt.toInstant().atOffset(ZoneOffset.UTC)
                        );
                    }
                }
            }
        }
    }

    public List<ConteudoDTO> listarPorTurma(Long idTurma) throws SQLException {

        List<ConteudoDTO> conteudos = new ArrayList<>();

        String sql = """
                SELECT *
                FROM public."CONTEUDOS"
                WHERE ? = ANY(id_turma)
                ORDER BY "created_at" DESC
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conteudos.add(montarConteudo(rs, idTurma));
                }
            }
        }

        return conteudos;
    }

    private ConteudoDTO montarConteudo(ResultSet rs, Long idTurma) throws SQLException {

        ConteudoDTO conteudo = new ConteudoDTO();

        conteudo.setId(rs.getLong("id"));
        conteudo.setIdTurma(idTurma);
        conteudo.setIdProfessor(rs.getLong("id_professor"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            conteudo.setCreatedAt(
                    createdAt.toInstant().atOffset(ZoneOffset.UTC)
            );
        }

        conteudo.setTitulo(rs.getString("titulo"));
        conteudo.setDescricao(rs.getString("descricao"));
        conteudo.setTipo(rs.getString("tipo"));
        conteudo.setUrl(rs.getString("url"));
        conteudo.setDuracaoEstimada(rs.getString("duracao_estimada"));
        conteudo.setPrioridade(rs.getString("prioridade"));

        return conteudo;
    }

    public void excluir(Long id) throws SQLException {

        String sql = """
                    
                DELETE FROM public."CONTEUDOS"
                    WHERE "id" = ?
                    """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }
}