// banco de conteudos centraliza materiais progresso e prazos

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

// persistencia de materiais esta classe filtra publicacoes e grava conclusoes remocoes e pedidos de revisao
@Repository
public class ConteudoRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final String URL_VIDEO_YOUTUBE =
            "LOWER(COALESCE(\"url\", '')) ~ '^(https?://)([[:alnum:]-]+\\.)*youtube\\.com(/|$)|^(https?://)youtu\\.be(/|$)'";
    private static final String URL_LIVRO_GOOGLE_BOOKS =
            "LOWER(COALESCE(\"url\", '')) ~ '^https?://(books|play)\\.google\\.com(/|$)'";

    // preparar consulta de conteudos este construtor recebe o acesso ao banco
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

    // cadastrar conteudo no banco este metodo grava o material e recupera identificador data e situacao
    public void inserir(ConteudoDTO conteudo) throws SQLException {
        String sql = """
                INSERT INTO public."CONTEUDOS"
                ("id_turma", "id_professor", "titulo", "descricao", "tipo", "url", "duracao_estimada", "prioridade", "duracao_estimada_min")
                VALUES (ARRAY[?::bigint], ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING "id", "created_at", "status"
                """;

        // gravar conteudo prepara os dados do material e converte falhas de cadastro
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

                // ler conteudo criado recupera os dados devolvidos pela gravacao e fecha o resultado ao terminar
                try (ResultSet rs = stmt.executeQuery()) {
                    // preencher conteudo criado guarda codigo situacao e data quando o banco devolve o registro
                    if (rs.next()) {
                        conteudo.setId(rs.getLong("id"));
                        conteudo.setStatus(rs.getString("status"));

                        Timestamp createdAt = rs.getTimestamp("created_at");

                        // preencher data do conteudo converte a data recebida apenas quando ela existe
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

    // listar materiais da turma no banco este metodo consulta os conteudos que nao foram removidos
    public List<ConteudoDTO> listarPorTurma(Long idTurma) throws SQLException {
        List<ConteudoDTO> conteudos = new ArrayList<>();

        String sql = """
                SELECT *
                FROM public."CONTEUDOS"
                WHERE ? = ANY("id_turma")
                AND "status" <> 'removido'
                ORDER BY "created_at" DESC
                """;

        // consultar conteudos da turma prepara o codigo da turma e converte falhas da busca
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idTurma);

                // ler materiais da turma percorre os resultados e fecha a consulta ao terminar
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
    // listar materiais ativos do evento este metodo consulta os conteudos associados em ordem de criacao
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

        // consultar conteudos do evento prepara o codigo do compromisso e converte falhas da busca
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idEvento);

                // ler materiais do evento monta a lista e fecha o resultado ao terminar
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

    // listar materiais de varios eventos este metodo agrupa os conteudos ativos por evento em uma consulta
    public Map<Long, List<ConteudoDTO>> listarPorEventos(List<Long> idsEventos) throws SQLException {
        Map<Long, List<ConteudoDTO>> conteudosPorEvento = new HashMap<>();

        // evitar consulta sem eventos devolve o agrupamento vazio quando nao existem codigos
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

        // consultar materiais de varios eventos envia os codigos juntos e converte falhas da busca
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setArray(1, stmt.getConnection().createArrayOf("bigint", idsEventos.toArray()));

                // agrupar materiais consultados reune os resultados por evento e fecha a consulta ao terminar
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

    // buscar materiais por turma e texto este metodo filtra os conteudos por busca e tipo de material
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

        // aplicar busca textual inclui o filtro em portugues e ingles quando existe termo informado
        if (temTermo) {
            sql.append(" AND \"busca\" @@ (websearch_to_tsquery('portuguese', ?) || websearch_to_tsquery('english', ?)) ");
        }

        // aplicar tipo de conteudo distingue videos livros links e outros tipos quando ha filtro
        if (temTipo) {
            switch (tipo) {
                case "video" -> sql.append(" AND \"tipo\" = 'link' AND ").append(URL_VIDEO_YOUTUBE);
                case "livro" -> sql.append(" AND \"tipo\" = 'link' AND ").append(URL_LIVRO_GOOGLE_BOOKS);
                case "link" -> sql.append(" AND \"tipo\" = ? AND NOT (")
                        .append(URL_VIDEO_YOUTUBE).append(" OR ").append(URL_LIVRO_GOOGLE_BOOKS).append(")");
                default -> sql.append(" AND \"tipo\" = ? ");
            }
        }

        // ordenar busca textual usa relevancia quando existe termo ou apenas a data quando nao existe
        if (temTermo) {
            sql.append(" ORDER BY ts_rank(\"busca\", websearch_to_tsquery('portuguese', ?) || websearch_to_tsquery('english', ?)) DESC, \"created_at\" DESC");
        } else {
            sql.append(" ORDER BY \"created_at\" DESC");
        }

        // consultar conteudos filtrados preenche os filtros da turma e converte falhas da busca
        try {
            jdbcTemplate.execute(sql.toString(), (PreparedStatementCallback<Void>) stmt -> {

                int i = 1;
                stmt.setLong(i++, idTurma);

                // preencher termos da pesquisa envia o texto aos dois idiomas quando a busca esta ativa
                if (temTermo) {
                    stmt.setString(i++, termo);
                    stmt.setString(i++, termo);
                }

                // preencher tipo simples envia o tipo quando ele nao depende da identificacao de video ou livro
                if (temTipo && !tipo.equals("video") && !tipo.equals("livro")) {
                    stmt.setString(i++, tipo);
                }

                // preencher termos da ordenacao usa a busca nos dois idiomas para calcular a relevancia
                if (temTermo) {
                    stmt.setString(i++, termo);
                    stmt.setString(i++, termo);
                }

                // ler busca da turma monta os materiais encontrados e fecha a consulta ao terminar
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

    // listar materiais do autor no banco este metodo consulta conteudos e seus vinculos com turmas
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

        // consultar materiais do professor envia o codigo do autor e converte falhas da busca
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idProfessor);

                // ler materiais pessoais monta conteudos e revisoes e fecha o resultado ao terminar
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ConteudoDTO conteudo = new ConteudoDTO();

                        conteudo.setId(rs.getLong("id"));

                        Long idTurma = rs.getLong("turma_id");

                        // preservar turma ausente evita tratar um vinculo vazio como codigo zero
                        if (rs.wasNull()) {
                            idTurma = null;
                        }

                        conteudo.setIdTurma(idTurma);
                        conteudo.setIdProfessor(rs.getLong("id_professor"));

                        Timestamp createdAt = rs.getTimestamp("created_at");

                        // preencher data do conteudo converte a data recebida apenas quando ela existe
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

                        // preencher data de revisao inclui a data do pedido quando ela existe
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

    // listar pagina de materiais do professor este metodo retorna dez conteudos sem repetir os compartilhados
    public List<ConteudoDTO> listarPorProfessorPaginado(Long idProfessor, int pagina, String busca, String status) throws SQLException {
        List<ConteudoDTO> conteudos = new ArrayList<>();
        long inicio = Math.max(0L, (long) pagina) * 10;


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

        // consultar pagina do professor aplica filtros e inicio da pagina e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                preencherFiltrosProfessor(stmt, idProfessor, busca, status);
                stmt.setLong(7, inicio);

                // ler pagina pessoal monta os materiais com o nome da turma e fecha a consulta
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

    // contar materiais filtrados do professor este metodo consulta o total que atende a busca e a situacao
    public int contarFiltradosPorProfessor(Long idProfessor, String busca, String status) throws SQLException {
        String sql = "SELECT COUNT(*) AS quantidade FROM public.\"CONTEUDOS\" c " + SQL_FILTROS_PROFESSOR;

        // contar busca pessoal aplica os filtros do professor e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                preencherFiltrosProfessor(stmt, idProfessor, busca, status);

                // ler total pessoal recupera a quantidade encontrada e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt("quantidade");
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // contar situacoes dos materiais do professor este metodo consulta os totais de ativos suspensos e removidos
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

        // consultar resumo do professor busca os totais por situacao e converte falhas do banco
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idProfessor);

                // ler totais pessoais guarda total ativos suspensos e removidos e fecha a consulta
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

    // preencher filtros do professor este metodo normaliza busca e situacao para a consulta de materiais
    private void preencherFiltrosProfessor(PreparedStatement stmt, Long idProfessor, String busca, String status) throws SQLException {
        String buscaTexto = busca == null ? "" : busca.trim();
        String statusTexto = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        // normalizar situacao pessoal usa todas as situacoes quando o filtro nao e reconhecido
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


    // listar pagina administrativa de materiais este metodo retorna dez conteudos com nomes de professor e turmas
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

        // consultar pagina administrativa aplica busca situacao e inicio da pagina e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                preencherFiltrosAdmin(stmt, busca, status);
                stmt.setLong(7, inicio);

                // ler pagina administrativa monta conteudos professor e turma e fecha a consulta
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


    // contar materiais filtrados da administracao este metodo consulta o total que atende a busca e a situacao
    public int contarFiltradosParaAdmin(String busca, String status) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."CONTEUDOS" c
                LEFT JOIN public."PROFESSORES" p ON p."id" = c."id_professor"
                LEFT JOIN public."USUARIOS" u ON u."id" = p."id_usuario"
                """ + SQL_FILTROS_ADMIN;

        // contar busca administrativa aplica os filtros de conteudo e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                preencherFiltrosAdmin(stmt, busca, status);

                // ler total administrativo recupera a quantidade encontrada e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt("quantidade");
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // preencher filtros administrativos de materiais este metodo normaliza as opcoes usadas na consulta
    private void preencherFiltrosAdmin(PreparedStatement stmt, String busca, String status) throws SQLException {
        String buscaTexto = busca == null ? "" : busca.trim();
        String statusTexto = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        // normalizar situacao administrativa usa todas as situacoes quando o filtro nao e reconhecido
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

    // listar materiais aguardando analise este metodo consulta conteudos suspensos com revisao solicitada
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

        // consultar materiais para analise busca conteudos pendentes e converte falhas do banco
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                // ler fila de analise monta materiais com seus professores e fecha a consulta
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

    // montar dados do conteudo este metodo organiza os campos consultados incluindo situacao e pedido de revisao
    private ConteudoDTO montarConteudo(ResultSet rs, Long idTurma) throws SQLException {
        ConteudoDTO conteudo = new ConteudoDTO();

        conteudo.setId(rs.getLong("id"));
        conteudo.setIdTurma(idTurma);
        conteudo.setIdProfessor(rs.getLong("id_professor"));

        Timestamp createdAt = rs.getTimestamp("created_at");

        // preencher data do conteudo converte a data recebida apenas quando ela existe
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

        // preencher pedido de revisao converte a data somente quando existe solicitacao registrada
        if (revisaoSolicitadaEm != null) {
            conteudo.setRevisaoSolicitadaEm(revisaoSolicitadaEm.toInstant().atOffset(ZoneOffset.UTC));
        }

        return conteudo;
    }

    // buscar autor do conteudo este metodo consulta o professor responsavel pelo material
    public Long buscarIdProfessor(Long idConteudo) throws SQLException {
        String sql = """
                SELECT "id_professor"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        // consultar autor do conteudo busca o professor pelo material e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Long>) stmt -> {

                stmt.setLong(1, idConteudo);

                // ler autor encontrado recupera o codigo do professor e fecha a consulta
                try (ResultSet rs = stmt.executeQuery()) {
                    // retornar professor do material devolve o codigo apenas quando a consulta encontrou um registro
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

    // consultar situacao do conteudo este metodo busca o estado atual do material no banco
    public String buscarStatus(Long idConteudo) throws SQLException {
        String sql = """
                SELECT "status"
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                """;

        // consultar situacao do material busca o estado do conteudo e converte falhas do banco
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<String>) stmt -> {

                stmt.setLong(1, idConteudo);

                // ler situacao encontrada recupera o estado e fecha a consulta ao terminar
                try (ResultSet rs = stmt.executeQuery()) {
                    // devolver situacao do conteudo usa o valor apenas quando a consulta encontrou um registro
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

    // verificar vinculo do conteudo este metodo confere se o material nao removido pertence a turma
    public boolean conteudoPertenceTurma(Long idConteudo, Long idTurma) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."CONTEUDOS"
                WHERE "id" = ?
                AND ? = ANY("id_turma")
                AND "status" <> 'removido'
                """;

        // conferir material da turma consulta o vinculo pelos dois codigos e converte falhas
        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idTurma);

                // ler quantidade de vinculos consulta a contagem e fecha o resultado ao terminar
                try (ResultSet rs = stmt.executeQuery()) {
                    // confirmar vinculo encontrado considera valido quando a contagem recebida e maior que zero
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

    public boolean usuarioTemAcessoAoConteudo(Long idConteudo, String tipoUsuario, Long idUsuario) throws SQLException {
        String sql;
        Object[] parametros;

        if ("aluno".equals(tipoUsuario)) {
            sql = """
                    SELECT EXISTS (
                        SELECT 1 FROM public."CONTEUDOS" c
                        JOIN public."TURMAS" t ON t."id" = ANY(c."id_turma")
                        WHERE c."id" = ? AND ? = ANY(t."id_alunos")
                    )
                    """;
            parametros = new Object[]{idConteudo, idUsuario};
        } else if ("professor".equals(tipoUsuario)) {
            sql = """
                    SELECT EXISTS (
                        SELECT 1 FROM public."CONTEUDOS" c
                        LEFT JOIN public."TURMAS" t ON t."id" = ANY(c."id_turma")
                        WHERE c."id" = ?
                        AND (c."id_professor" = ?
                             OR ? = ANY(COALESCE(t."id_professores", ARRAY[]::bigint[]))
                             OR t."id_proprietario" = ?)
                    )
                    """;
            parametros = new Object[]{idConteudo, idUsuario, idUsuario, idUsuario};
        } else {
            return false;
        }

        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, parametros));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // alterar situacao do conteudo este metodo grava o estado informado para o material
    public void atualizarStatus(Long idConteudo, String status) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = ?
                WHERE "id" = ?
                """;

        // gravar situacao do conteudo atualiza o estado pelo codigo e converte falhas do banco
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

    // delete conteudo do autor no banco este metodo marca o material como removido e limpa o pedido de revisao
    public boolean removerConteudoProfessor(Long idConteudo, Long idProfessor) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'removido',
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                AND "id_professor" = ?
                """;

        // delete conteudo do professor aplica a remocao com o codigo do autor e converte falhas
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

    // reabrir conteudo no banco este metodo registra a reabertura feita pelo autor ou responsavel da turma
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

        // gravar reabertura do conteudo aplica as restricoes de autor e turma e converte falhas
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

    // cadastrar pedido de revisao este metodo marca o material suspenso do professor para analise
    public boolean solicitarRevisao(Long idConteudo, Long idProfessor) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "revisao_solicitada" = true,
                    "revisao_solicitada_em" = now()
                WHERE "id" = ?
                AND "id_professor" = ?
                AND "status" = 'suspenso_denuncia'
                """;

        // gravar pedido de revisao registra a solicitacao do professor e converte falhas do banco
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

    // delete conteudo por identificador este metodo marca o material como removido sem apagar o registro
    public void excluir(Long id) throws SQLException {
        String sql = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'removido',
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                """;

        // delete conteudo no banco executa a remocao pelo codigo e converte falhas
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

    // editar dados do conteudo este metodo atualiza titulo descricao endereco duracao e tipo
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

        // gravar edicao do material atualiza titulo descricao endereco duracao e tipo e converte falhas
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

    // gravar conclusao do aluno este metodo registra o material concluido sem repetir uma conclusao existente
    public void marcarConcluido(Long idAluno, Long idConteudo) throws SQLException {
        String sql = """
                INSERT INTO public."PROGRESSO_CONTEUDO" ("id_aluno", "id_conteudo")
                VALUES (?, ?)
                ON CONFLICT ("id_aluno", "id_conteudo") DO NOTHING
                """;

        // registrar progresso concluido grava a conclusao do aluno e converte falhas do banco
        try {
            jdbcTemplate.update(sql, idAluno, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // delete conclusao do aluno este metodo apaga a marcacao e deixa o material pendente
    public void desmarcarConcluido(Long idAluno, Long idConteudo) throws SQLException {
        String sql = """
                DELETE FROM public."PROGRESSO_CONTEUDO"
                WHERE "id_aluno" = ?
                AND "id_conteudo" = ?
                """;

        // desmarcar progresso concluido retira a conclusao do aluno e converte falhas do banco
        try {
            jdbcTemplate.update(sql, idAluno, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // listar conclusoes do aluno na turma este metodo consulta os identificadores dos materiais concluidos
    public Set<Long> listarConcluidosDoAluno(Long idAluno, Long idTurma) throws SQLException {
        String sql = """
                SELECT p."id_conteudo"
                FROM public."PROGRESSO_CONTEUDO" p
                INNER JOIN public."CONTEUDOS" c
                    ON c."id" = p."id_conteudo"
                WHERE p."id_aluno" = ?
                AND ? = ANY(c."id_turma")
                """;

        // consultar conclusoes da turma busca os materiais feitos pelo aluno e converte falhas
        try {
            return new HashSet<>(jdbcTemplate.queryForList(sql, Long.class, idAluno, idTurma));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // listar conclusoes de varios alunos este metodo agrupa os materiais concluidos por aluno
    public Map<Long, Set<Long>> listarConcluidosDosAlunos(List<Long> idsAlunos) throws SQLException {
        Map<Long, Set<Long>> concluidosPorAluno = new HashMap<>();

        // evitar busca sem alunos devolve o agrupamento vazio quando nao existem codigos
        if (idsAlunos.isEmpty()) {
            return concluidosPorAluno;
        }

        String sql = """
                SELECT "id_aluno", "id_conteudo"
                FROM public."PROGRESSO_CONTEUDO"
                WHERE "id_aluno" = ANY(?)
                """;

        // consultar conclusoes em grupo envia os codigos dos alunos juntos e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setArray(1, stmt.getConnection().createArrayOf("bigint", idsAlunos.toArray()));

                // ler conclusoes agrupadas reune os materiais por aluno e fecha a consulta
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

    // buscar prazos encerrados este metodo encontra materiais sem evento futuro nem reabertura apos o ultimo prazo
    public Map<Long, LocalDateTime> listarPrazosEncerrados(List<Long> idsConteudos, LocalDateTime referencia) throws SQLException {
        Map<Long, LocalDateTime> prazoPorConteudo = new HashMap<>();

        // evitar busca sem materiais devolve os prazos vazios quando nao existem conteudos
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

        // consultar prazos encerrados usa a data de referencia e os conteudos e converte falhas
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setTimestamp(1, Timestamp.valueOf(referencia));
                stmt.setTimestamp(2, Timestamp.valueOf(referencia));
                stmt.setArray(3, stmt.getConnection().createArrayOf("bigint", idsConteudos.toArray()));

                // ler prazos dos materiais filtra os resultados e fecha a consulta ao terminar
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ultimoPrazo = rs.getTimestamp("ultimo_prazo");
                        Timestamp reabertoEm = rs.getTimestamp("reaberto_em");

                        // ignorar prazo ainda disponivel descarta materiais sem prazo com evento futuro ou reabertos apos o prazo
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

    // somar duracao das conclusoes recentes este metodo calcula os minutos estimados dos materiais concluidos por aluno
    public Map<Long, Integer> minutosConcluidosRecentes(List<Long> idsAlunos, int dias, int duracaoPadraoMin) throws SQLException {
        Map<Long, Integer> minutosPorAluno = new HashMap<>();

        // evitar soma sem alunos retorna os tempos vazios quando nao existem alunos informados
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

        // consultar estudo recente aplica duracao padrao alunos e periodo e converte falhas do banco
        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setInt(1, duracaoPadraoMin);
                stmt.setArray(2, stmt.getConnection().createArrayOf("bigint", idsAlunos.toArray()));
                stmt.setInt(3, dias);

                // ler minutos por aluno guarda os tempos encontrados e fecha a consulta
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


    // tratar erro do banco de conteudos este metodo recupera a falha original ou cria uma falha de consulta
    private SQLException converterErro(DataAccessException erro) {
        Throwable causa = erro;
        while (causa != null) {
            // recuperar falha do banco devolve a causa original quando ela e uma falha de consulta
            if (causa instanceof SQLException sqlException) {
                return sqlException;
            }
            causa = causa.getCause();
        }
        return new SQLException(erro.getMessage(), erro);
    }
}
