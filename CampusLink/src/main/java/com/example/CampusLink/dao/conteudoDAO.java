package com.example.CampusLink.dao;

import com.example.CampusLink.dto.ConteudoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
                ( "id_turma", "id_professor", "titulo", "descricao", "tipo", "url", "duracao_estimada", "prioridade")
                VALUES (ARRAY[?::bigint], ?, ?, ?, ?, ?, ?, ?)
                RETURNING "id", "created_at", "status"
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

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
                    conteudo.setStatus(rs.getString("status"));

                    Timestamp createdAt = rs.getTimestamp("created_at");

                    if (createdAt != null) {
                        conteudo.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
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
                WHERE ? = ANY("id_turma")
                AND "status" <> 'removido'
                ORDER BY "created_at" DESC
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    conteudos.add(montarConteudo(rs, idTurma));
                }
            }
        }

        return conteudos;
    }

    public List<ConteudoDTO> listarPorProfessor(Long idProfessor) throws SQLException {

        List<ConteudoDTO> conteudos = new ArrayList<>();

        String sql = """
                SELECT
                    c.*,
                    t."id" AS "turma_id",
                    t."nome_turma"
                FROM public."CONTEUDOS" c
                LEFT JOIN public."TURMAS" t
                    ON t."id" = ANY(c."id_turma")
                WHERE c."id_professor" = ?
                ORDER BY c."created_at" DESC
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idProfessor);

            try (ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {

                    ConteudoDTO conteudo = new ConteudoDTO();

                    conteudo.setId(rs.getLong("id"));

                    Long idTurma = rs.getLong("turma_id");

                    if (rs.wasNull()) {
                        idTurma = null;
                    }

                    conteudo.setIdTurma(idTurma);
                    conteudo.setIdProfessor(rs.getLong("id_professor"));

                    Timestamp createdAt = rs.getTimestamp("created_at");

                    if (createdAt != null) {
                        conteudo.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                    }

                    conteudo.setTitulo(rs.getString("titulo"));
                    conteudo.setDescricao(rs.getString("descricao"));
                    conteudo.setTipo(rs.getString("tipo"));
                    conteudo.setUrl(rs.getString("url"));
                    conteudo.setDuracaoEstimada(rs.getString("duracao_estimada"));
                    conteudo.setPrioridade(rs.getString("prioridade"));
                    conteudo.setStatus(rs.getString("status"));

                    conteudo.setComentarioAdmin(
                            rs.getString("comentario_admin")
                    );

                    conteudo.setNomeTurma(rs.getString("nome_turma"));
                    conteudo.setRevisaoSolicitada(rs.getBoolean("revisao_solicitada"));

                    Timestamp revisaoSolicitadaEm = rs.getTimestamp("revisao_solicitada_em");

                    if (revisaoSolicitadaEm != null) {
                        conteudo.setRevisaoSolicitadaEm(
                                revisaoSolicitadaEm.toInstant().atOffset(ZoneOffset.UTC)
                        );
                    }

                    conteudos.add(conteudo);
                }
            }
        }

        return conteudos;
    }

    public List<ConteudoDTO> listarParaAnaliseAdmin() throws SQLException {

        List<ConteudoDTO> conteudos = new ArrayList<>();

        String sql = """
                SELECT c.*, u."nome" AS nome_professor
                FROM public."CONTEUDOS" c
                INNER JOIN public."PROFESSORES" p
                    ON p."id" = c."id_professor"
                INNER JOIN public."USUARIOS" u
                    ON u."id" = p."id_usuario"
                WHERE c."status" = 'suspenso_denuncia'
                AND c."revisao_solicitada" = true
                ORDER BY c."revisao_solicitada_em" ASC, c."id" ASC
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {

                ConteudoDTO conteudo = montarConteudo(rs, null);

                conteudo.setNomeProfessor(
                        rs.getString("nome_professor")
                );

                conteudos.add(conteudo);
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
        conteudo.setStatus(rs.getString("status"));

        conteudo.setComentarioAdmin(
                rs.getString("comentario_admin")
        );

        conteudo.setRevisaoSolicitada(
                rs.getBoolean("revisao_solicitada")
        );

        Timestamp revisaoSolicitadaEm =
                rs.getTimestamp("revisao_solicitada_em");

        if (revisaoSolicitadaEm != null) {
            conteudo.setRevisaoSolicitadaEm(
                    revisaoSolicitadaEm.toInstant().atOffset(ZoneOffset.UTC)
            );
        }

        return conteudo;
    }

    public Long buscarIdProfessor(Long idConteudo) throws SQLException {

        String sql = """
                SELECT "id_professor"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getLong("id_professor");
                }
            }
        }

        return null;
    }

    public String buscarStatus(Long idConteudo) throws SQLException {

        String sql = """
                SELECT "status"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        }

        return null;
    }

    public boolean conteudoPertenceTurma(Long idConteudo, Long idTurma) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                AND ? = ANY("id_turma")
                AND "status" <> 'removido'
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);
            stmt.setLong(2, idTurma);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt("quantidade") > 0;
                }
            }
        }

        return false;
    }

    public void atualizarStatus(
            Long idConteudo,
            String status
    ) throws SQLException {

        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = ?
                WHERE "id" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setLong(2, idConteudo);
            stmt.executeUpdate();
        }
    }

    public boolean removerConteudoProfessor(Long idConteudo, Long idProfessor) throws SQLException {

        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'removido',
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                AND "id_professor" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);
            stmt.setLong(2, idProfessor);

            return stmt.executeUpdate() > 0;
        }
    }

    public boolean solicitarRevisao(Long idConteudo, Long idProfessor) throws SQLException {

        String sql = """
                UPDATE public."CONTEUDOS"
                SET "revisao_solicitada" = true,
                    "revisao_solicitada_em" = now()
                WHERE "id" = ?
                AND "id_professor" = ?
                AND "status" = 'suspenso_denuncia'
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idConteudo);
            stmt.setLong(2, idProfessor);

            return stmt.executeUpdate() > 0;
        }
    }

    public void excluir(Long id) throws SQLException {

        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'removido',
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    public void atualizar(ConteudoDTO conteudo) throws SQLException {

        String sql = """
                UPDATE public."CONTEUDOS"
                SET "titulo" = ?,
                    "url" = ?,
                    "duracao_estimada" = ?,
                    "tipo" = ?
                WHERE "id" = ?
                """;

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, conteudo.getTitulo());
            stmt.setString(2, conteudo.getUrl());
            stmt.setString(3, conteudo.getDuracaoEstimada());
            stmt.setString(4, conteudo.getTipo());
            stmt.setLong(5, conteudo.getId());

            stmt.executeUpdate();
        }
    }
}