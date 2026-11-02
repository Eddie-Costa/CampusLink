
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ConteudoDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Repository
public class ConteudoRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final String URL_VIDEO_YOUTUBE =
            "LOWER(COALESCE(\"url\", '')) ~ '^(https?://)([[:alnum:]-]+\\.)*youtube\\.com(/|$)|^(https?://)youtu\\.be(/|$)'";
    private static final String URL_LIVRO_GOOGLE_BOOKS =
            "LOWER(COALESCE(\"url\", '')) ~ '^https?://(books|play)\\.google\\.com(/|$)'";

    public ConteudoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SQL_FILTROS_PROFESSOR = """
            WHERE c."id_professor" = ?
            AND (? = 'todos' OR c."status" = ?)
            AND (
                POSITION(LOWER(?) IN LOWER(COALESCE(c."titulo", ''))) > 0
                OR POSITION(LOWER(?) IN LOWER(COALESCE(c."status", ''))) > 0
                OR EXISTS (
                    SELECT 1 FROM public."TURMAS" t
                    WHERE t."id" = ANY(c."id_turma")
                    AND POSITION(LOWER(?) IN LOWER(COALESCE(t."nome_turma", ''))) > 0
                )
            )
            """;

    private static final String SQL_FILTROS_ADMIN = """
            WHERE (? = 'todos' OR c."status" = ?)
            AND (
                POSITION(LOWER(?) IN LOWER(COALESCE(c."titulo", ''))) > 0
                OR POSITION(LOWER(?) IN LOWER(COALESCE(c."tipo", ''))) > 0
                OR POSITION(LOWER(?) IN LOWER(COALESCE(u."nome", ''))) > 0
                OR EXISTS (
                    SELECT 1 FROM public."TURMAS" t
                    WHERE t."id" = ANY(c."id_turma")
                    AND POSITION(LOWER(?) IN LOWER(COALESCE(t."nome_turma", ''))) > 0
                )
            )
            """;

    public void inserir(ConteudoDTO conteudo) throws SQLException {
        String sql = """
                INSERT INTO public."CONTEUDOS"
                ("id_turma", "id_professor", "titulo", "descricao", "tipo", "url", "duracao_estimada", "prioridade", "duracao_estimada_min")
                VALUES (ARRAY[?::bigint], ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING "id", "created_at", "status"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, conteudo.getIdTurma());
                stmt.setLong(2, conteudo.getIdProfessor());
                stmt.setString(3, conteudo.getTitulo());

                String descricao = conteudo.getDescricao();
                stmt.setString(4, descricao == null ? "" : descricao);

                stmt.setString(5, conteudo.getTipo());
                stmt.setString(6, conteudo.getUrl());
                stmt.setString(7, conteudo.getDuracaoEstimada());
                stmt.setString(8, conteudo.getPrioridade());
                stmt.setObject(9, conteudo.getDuracaoEstimadaMin(), Types.INTEGER);

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
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idTurma);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conteudos.add(montarConteudo(rs, idTurma));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudos;
    }
    public List<ConteudoDTO> listarPorEvento(Long idEvento) throws SQLException {
        List<ConteudoDTO> conteudos = new ArrayList<>();

        String sql = """
                SELECT c.*
                FROM public."EVENTO_CONTEUDO" ec
                INNER JOIN public."CONTEUDOS" c
                    ON c."id" = ec."id_conteudo"
                WHERE ec."id_evento" = ?
                AND c."status" = 'ativo'
                ORDER BY c."created_at" ASC, c."id" ASC
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idEvento);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conteudos.add(montarConteudo(rs, null));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudos;
    }

    public Map<Long, List<ConteudoDTO>> listarPorEventos(List<Long> idsEventos) throws SQLException {
        Map<Long, List<ConteudoDTO>> conteudosPorEvento = new HashMap<>();

        if (idsEventos.isEmpty()) {
            return conteudosPorEvento;
        }

        String sql = """
                SELECT c.*, ec."id_evento" AS evento_id
                FROM public."EVENTO_CONTEUDO" ec
                INNER JOIN public."CONTEUDOS" c
                    ON c."id" = ec."id_conteudo"
                WHERE ec."id_evento" = ANY(?)
                AND c."status" = 'ativo'
                ORDER BY c."created_at" ASC, c."id" ASC
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setArray(1, stmt.getConnection().createArrayOf("bigint", idsEventos.toArray()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conteudosPorEvento.computeIfAbsent(rs.getLong("evento_id"), idEvento -> new ArrayList<>())
                                .add(montarConteudo(rs, null));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudosPorEvento;
    }

    public List<ConteudoDTO> buscarPorTurma(Long idTurma, String termo, String tipo) throws SQLException {

        List<ConteudoDTO> conteudos = new ArrayList<>();

        boolean temTermo = termo != null && !termo.isBlank();
        boolean temTipo = tipo != null && !tipo.isBlank();

        StringBuilder sql = new StringBuilder("""
            SELECT *
            FROM public."CONTEUDOS"
            WHERE ? = ANY("id_turma")
            AND "status" <> 'removido'
            """);

        if (temTermo) {
            sql.append(" AND \"busca\" @@ (websearch_to_tsquery('portuguese', ?) || websearch_to_tsquery('english', ?)) ");
        }

        if (temTipo) {
            switch (tipo) {
                case "video" -> sql.append(" AND \"tipo\" = 'link' AND ").append(URL_VIDEO_YOUTUBE);
                case "livro" -> sql.append(" AND \"tipo\" = 'link' AND ").append(URL_LIVRO_GOOGLE_BOOKS);
                case "link" -> sql.append(" AND \"tipo\" = ? AND NOT (")
                        .append(URL_VIDEO_YOUTUBE).append(" OR ").append(URL_LIVRO_GOOGLE_BOOKS).append(")");
                default -> sql.append(" AND \"tipo\" = ? ");
            }
        }

        if (temTermo) {
            sql.append(" ORDER BY ts_rank(\"busca\", websearch_to_tsquery('portuguese', ?) || websearch_to_tsquery('english', ?)) DESC, \"created_at\" DESC");
        } else {
            sql.append(" ORDER BY \"created_at\" DESC");
        }

        try {
            jdbcTemplate.execute(sql.toString(), (PreparedStatementCallback<Void>) stmt -> {

                int i = 1;
                stmt.setLong(i++, idTurma);

                if (temTermo) {
                    stmt.setString(i++, termo);
                    stmt.setString(i++, termo);
                }

                if (temTipo && !tipo.equals("video") && !tipo.equals("livro")) {
                    stmt.setString(i++, tipo);
                }

                if (temTermo) {
                    stmt.setString(i++, termo);
                    stmt.setString(i++, termo);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conteudos.add(montarConteudo(rs, idTurma));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

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
                        conteudo.setDuracaoEstimadaMin(rs.getObject("duracao_estimada_min", Integer.class));
                        conteudo.setPrioridade(rs.getString("prioridade"));
                        conteudo.setStatus(rs.getString("status"));
                        conteudo.setComentarioAdmin(rs.getString("comentario_admin"));
                        conteudo.setNomeTurma(rs.getString("nome_turma"));
                        conteudo.setRevisaoSolicitada(rs.getBoolean("revisao_solicitada"));

                        Timestamp revisaoSolicitadaEm = rs.getTimestamp("revisao_solicitada_em");

                        if (revisaoSolicitadaEm != null) {
                            conteudo.setRevisaoSolicitadaEm(revisaoSolicitadaEm.toInstant().atOffset(ZoneOffset.UTC));
                        }

                        conteudos.add(conteudo);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudos;
    }

    public List<ConteudoDTO> listarPorProfessorPaginado(Long idProfessor, int pagina, String busca, String status) throws SQLException {
        List<ConteudoDTO> conteudos = new ArrayList<>();
        long inicio = Math.max(0L, (long) pagina) * 10;

        // cada conteudo aparece uma vez mesmo quando pertence a varias turmas
        String sql = """
                SELECT c.*,
                    (SELECT MIN(t."id") FROM public."TURMAS" t
                     WHERE t."id" = ANY(c."id_turma")) AS turma_id,
                    (SELECT STRING_AGG(t."nome_turma", ', ' ORDER BY t."nome_turma", t."id")
                     FROM public."TURMAS" t WHERE t."id" = ANY(c."id_turma")) AS nome_turma
                FROM public."CONTEUDOS" c
                """ + SQL_FILTROS_PROFESSOR + """
                ORDER BY c."created_at" DESC NULLS LAST, c."id" DESC
                LIMIT 10 OFFSET ?
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                preencherFiltrosProfessor(stmt, idProfessor, busca, status);
                stmt.setLong(7, inicio);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Long idTurma = rs.getObject("turma_id", Long.class);

                        ConteudoDTO conteudo = montarConteudo(rs, idTurma);
                        conteudo.setNomeTurma(rs.getString("nome_turma"));

                        conteudos.add(conteudo);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudos;
    }

    public int contarFiltradosPorProfessor(Long idProfessor, String busca, String status) throws SQLException {
        String sql = "SELECT COUNT(*) AS quantidade FROM public.\"CONTEUDOS\" c " + SQL_FILTROS_PROFESSOR;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                preencherFiltrosProfessor(stmt, idProfessor, busca, status);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt("quantidade");
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public Map<String, Integer> contarPorProfessor(Long idProfessor) throws SQLException {
        Map<String, Integer> totais = new HashMap<>();

        String sql = """
                SELECT COUNT(*) AS total,
                    COUNT(CASE WHEN "status" = 'ativo' THEN 1 END) AS ativos,
                    COUNT(CASE WHEN "status" = 'suspenso_denuncia' THEN 1 END) AS suspensos,
                    COUNT(CASE WHEN "status" = 'removido' THEN 1 END) AS removidos
                FROM public."CONTEUDOS"
                WHERE "id_professor" = ?
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idProfessor);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();

                    totais.put("totalConteudos", rs.getInt("total"));
                    totais.put("totalAtivos", rs.getInt("ativos"));
                    totais.put("totalSuspensos", rs.getInt("suspensos"));
                    totais.put("totalRemovidos", rs.getInt("removidos"));
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return totais;
    }

    private void preencherFiltrosProfessor(PreparedStatement stmt, Long idProfessor, String busca, String status) throws SQLException {
        String buscaTexto = busca == null ? "" : busca.trim();
        String statusTexto = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        if (!"ativo".equals(statusTexto) && !"suspenso_denuncia".equals(statusTexto) && !"removido".equals(statusTexto)) {
            statusTexto = "todos";
        }

        stmt.setLong(1, idProfessor);
        stmt.setString(2, statusTexto);
        stmt.setString(3, statusTexto);
        stmt.setString(4, buscaTexto);
        stmt.setString(5, buscaTexto);
        stmt.setString(6, buscaTexto);
    }

    // lista dez conteudos por pagina para o administrador
    public List<ConteudoDTO> listarTodosParaAdminPaginado(int pagina, String busca, String status) throws SQLException {
        List<ConteudoDTO> conteudos = new ArrayList<>();
        long inicio = Math.max(0L, (long) pagina) * 10;

        String sql = """
                SELECT c.*,
                    u."nome" AS nome_professor,
                    (SELECT MIN(t."id") FROM public."TURMAS" t
                     WHERE t."id" = ANY(c."id_turma")) AS turma_id,
                    (SELECT STRING_AGG(t."nome_turma", ', ' ORDER BY t."nome_turma", t."id")
                     FROM public."TURMAS" t WHERE t."id" = ANY(c."id_turma")) AS nome_turma
                FROM public."CONTEUDOS" c
                LEFT JOIN public."PROFESSORES" p ON p."id" = c."id_professor"
                LEFT JOIN public."USUARIOS" u ON u."id" = p."id_usuario"
                """ + SQL_FILTROS_ADMIN + """
                ORDER BY c."created_at" DESC NULLS LAST, c."id" DESC
                LIMIT 10 OFFSET ?
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                preencherFiltrosAdmin(stmt, busca, status);
                stmt.setLong(7, inicio);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Long idTurma = rs.getObject("turma_id", Long.class);

                        ConteudoDTO conteudo = montarConteudo(rs, idTurma);
                        conteudo.setNomeProfessor(rs.getString("nome_professor"));
                        conteudo.setNomeTurma(rs.getString("nome_turma"));
                        conteudos.add(conteudo);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return conteudos;
    }

    // conta os conteudos encontrados para montar a paginacao
    public int contarFiltradosParaAdmin(String busca, String status) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."CONTEUDOS" c
                LEFT JOIN public."PROFESSORES" p ON p."id" = c."id_professor"
                LEFT JOIN public."USUARIOS" u ON u."id" = p."id_usuario"
                """ + SQL_FILTROS_ADMIN;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                preencherFiltrosAdmin(stmt, busca, status);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt("quantidade");
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    private void preencherFiltrosAdmin(PreparedStatement stmt, String busca, String status) throws SQLException {
        String buscaTexto = busca == null ? "" : busca.trim();
        String statusTexto = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        if (!"ativo".equals(statusTexto) && !"suspenso_denuncia".equals(statusTexto) && !"removido".equals(statusTexto)) {
            statusTexto = "todos";
        }

        stmt.setString(1, statusTexto);
        stmt.setString(2, statusTexto);
        stmt.setString(3, buscaTexto);
        stmt.setString(4, buscaTexto);
        stmt.setString(5, buscaTexto);
        stmt.setString(6, buscaTexto);
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

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        ConteudoDTO conteudo = montarConteudo(rs, null);
                        conteudo.setNomeProfessor(rs.getString("nome_professor"));
                        conteudos.add(conteudo);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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
            conteudo.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        conteudo.setTitulo(rs.getString("titulo"));
        conteudo.setDescricao(rs.getString("descricao"));
        conteudo.setTipo(rs.getString("tipo"));
        conteudo.setUrl(rs.getString("url"));
        conteudo.setDuracaoEstimada(rs.getString("duracao_estimada"));
        conteudo.setDuracaoEstimadaMin(rs.getObject("duracao_estimada_min", Integer.class));
        conteudo.setPrioridade(rs.getString("prioridade"));
        conteudo.setStatus(rs.getString("status"));
        conteudo.setComentarioAdmin(rs.getString("comentario_admin"));
        conteudo.setRevisaoSolicitada(rs.getBoolean("revisao_solicitada"));

        Timestamp revisaoSolicitadaEm = rs.getTimestamp("revisao_solicitada_em");

        if (revisaoSolicitadaEm != null) {
            conteudo.setRevisaoSolicitadaEm(revisaoSolicitadaEm.toInstant().atOffset(ZoneOffset.UTC));
        }

        return conteudo;
    }

    public Long buscarIdProfessor(Long idConteudo) throws SQLException {
        String sql = """
                SELECT "id_professor"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Long>) stmt -> {

                stmt.setLong(1, idConteudo);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("id_professor");
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public String buscarStatus(Long idConteudo) throws SQLException {
        String sql = """
                SELECT "status"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<String>) stmt -> {

                stmt.setLong(1, idConteudo);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("status");
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean conteudoPertenceTurma(Long idConteudo, Long idTurma) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                AND ? = ANY("id_turma")
                AND "status" <> 'removido'
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idTurma);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("quantidade") > 0;
                    }
                }
                return false;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void atualizarStatus(Long idConteudo, String status) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = ?
                WHERE "id" = ?
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setString(1, status);
                stmt.setLong(2, idConteudo);
                stmt.executeUpdate();
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idProfessor);

                return stmt.executeUpdate() > 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean reabrir(Long idConteudo, Long idProfessor, Long idTurma) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS" c
                SET "reaberto_em" = now()
                WHERE c."id" = ?
                AND c."status" = 'ativo'
                AND (c."id_professor" = ?
                     OR EXISTS (SELECT 1 FROM public."TURMAS" t
                                WHERE t."id" = ?
                                AND t."id_proprietario" = ?
                                AND t."id" = ANY(c."id_turma")))
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idProfessor);
                stmt.setObject(3, idTurma, Types.BIGINT);
                stmt.setLong(4, idProfessor);

                return stmt.executeUpdate() > 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idProfessor);

                return stmt.executeUpdate() > 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
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

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, id);
                stmt.executeUpdate();
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void atualizar(ConteudoDTO conteudo) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "titulo" = ?,
                    "descricao" = ?,
                    "url" = ?,
                    "duracao_estimada" = ?,
                    "duracao_estimada_min" = ?,
                    "tipo" = ?
                WHERE "id" = ?
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setString(1, conteudo.getTitulo());

                String descricao = conteudo.getDescricao();
                stmt.setString(2, descricao == null ? "" : descricao);

                stmt.setString(3, conteudo.getUrl());
                stmt.setString(4, conteudo.getDuracaoEstimada());
                stmt.setObject(5, conteudo.getDuracaoEstimadaMin(), Types.INTEGER);
                stmt.setString(6, conteudo.getTipo());
                stmt.setLong(7, conteudo.getId());

                stmt.executeUpdate();
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void marcarConcluido(Long idAluno, Long idConteudo) throws SQLException {
        String sql = """
                INSERT INTO public."PROGRESSO_CONTEUDO" ("id_aluno", "id_conteudo")
                VALUES (?, ?)
                ON CONFLICT ("id_aluno", "id_conteudo") DO NOTHING
                """;

        try {
            jdbcTemplate.update(sql, idAluno, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void desmarcarConcluido(Long idAluno, Long idConteudo) throws SQLException {
        String sql = """
                DELETE FROM public."PROGRESSO_CONTEUDO"
                WHERE "id_aluno" = ?
                AND "id_conteudo" = ?
                """;

        try {
            jdbcTemplate.update(sql, idAluno, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public Set<Long> listarConcluidosDoAluno(Long idAluno, Long idTurma) throws SQLException {
        String sql = """
                SELECT p."id_conteudo"
                FROM public."PROGRESSO_CONTEUDO" p
                INNER JOIN public."CONTEUDOS" c
                    ON c."id" = p."id_conteudo"
                WHERE p."id_aluno" = ?
                AND ? = ANY(c."id_turma")
                """;

        try {
            return new HashSet<>(jdbcTemplate.queryForList(sql, Long.class, idAluno, idTurma));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public Map<Long, Set<Long>> listarConcluidosDosAlunos(List<Long> idsAlunos) throws SQLException {
        Map<Long, Set<Long>> concluidosPorAluno = new HashMap<>();

        if (idsAlunos.isEmpty()) {
            return concluidosPorAluno;
        }

        String sql = """
                SELECT "id_aluno", "id_conteudo"
                FROM public."PROGRESSO_CONTEUDO"
                WHERE "id_aluno" = ANY(?)
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setArray(1, stmt.getConnection().createArrayOf("bigint", idsAlunos.toArray()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        concluidosPorAluno.computeIfAbsent(rs.getLong("id_aluno"), idAluno -> new HashSet<>())
                                .add(rs.getLong("id_conteudo"));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return concluidosPorAluno;
    }

    public Map<Long, LocalDateTime> listarPrazosEncerrados(List<Long> idsConteudos, LocalDateTime referencia) throws SQLException {
        Map<Long, LocalDateTime> prazoPorConteudo = new HashMap<>();

        if (idsConteudos.isEmpty()) {
            return prazoPorConteudo;
        }

        String sql = """
                SELECT c."id",
                       max(e."data_hora") FILTER (WHERE e."data_hora" < ?) AS "ultimo_prazo",
                       bool_or(e."data_hora" >= ?) AS "tem_evento_futuro",
                       c."reaberto_em" AT TIME ZONE 'America/Sao_Paulo' AS "reaberto_em"
                FROM public."CONTEUDOS" c
                INNER JOIN public."EVENTO_CONTEUDO" ec ON ec."id_conteudo" = c."id"
                INNER JOIN public."EVENTOS" e ON e."id" = ec."id_evento" AND e."status" = 'ATIVO'
                WHERE c."id" = ANY(?)
                GROUP BY c."id", c."reaberto_em"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setTimestamp(1, Timestamp.valueOf(referencia));
                stmt.setTimestamp(2, Timestamp.valueOf(referencia));
                stmt.setArray(3, stmt.getConnection().createArrayOf("bigint", idsConteudos.toArray()));

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ultimoPrazo = rs.getTimestamp("ultimo_prazo");
                        Timestamp reabertoEm = rs.getTimestamp("reaberto_em");

                        if (ultimoPrazo == null || rs.getBoolean("tem_evento_futuro")
                                || (reabertoEm != null && reabertoEm.after(ultimoPrazo))) {
                            continue;
                        }
                        prazoPorConteudo.put(rs.getLong("id"), ultimoPrazo.toLocalDateTime());
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return prazoPorConteudo;
    }

    public Map<Long, Integer> minutosConcluidosRecentes(List<Long> idsAlunos, int dias, int duracaoPadraoMin) throws SQLException {
        Map<Long, Integer> minutosPorAluno = new HashMap<>();

        if (idsAlunos.isEmpty()) {
            return minutosPorAluno;
        }

        String sql = """
                SELECT p."id_aluno", SUM(COALESCE(c."duracao_estimada_min", ?)) AS "minutos"
                FROM public."PROGRESSO_CONTEUDO" p
                INNER JOIN public."CONTEUDOS" c
                    ON c."id" = p."id_conteudo"
                WHERE p."id_aluno" = ANY(?)
                AND p."concluido_em" >= now() - make_interval(days => ?)
                GROUP BY p."id_aluno"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setInt(1, duracaoPadraoMin);
                stmt.setArray(2, stmt.getConnection().createArrayOf("bigint", idsAlunos.toArray()));
                stmt.setInt(3, dias);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        minutosPorAluno.put(rs.getLong("id_aluno"), rs.getInt("minutos"));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return minutosPorAluno;
    }

    // preserva o contrato de erro usado pelos consumidores
    private SQLException converterErro(DataAccessException erro) {
        Throwable causa = erro;
        while (causa != null) {
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
