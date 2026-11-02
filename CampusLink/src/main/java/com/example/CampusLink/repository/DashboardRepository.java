package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DashboardFiltro;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public Long buscarIdentidade(String email, String tipo) {
        String tabela = switch (tipo) {
            case "aluno" -> "ALUNOS";
            case "professor" -> "PROFESSORES";
            case "admin" -> "ADMINISTRADOR";
            default -> throw new IllegalArgumentException("Perfil inválido");
        };
        List<Long> ids = jdbc.queryForList("SELECT p.id FROM public.\"" + tabela + "\" p "
                + "JOIN public.\"USUARIOS\" u ON u.id = p.id_usuario "
                + "WHERE lower(u.email) = lower(:email) AND u.status = true AND u.perfil = :perfil",
                Map.of("email", email, "perfil", tabela), Long.class);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public MapSqlParameterSource parametros(String tipo, long id, DashboardFiltro filtro) {
        return new MapSqlParameterSource()
                .addValue("tipo", tipo).addValue("pessoa", id).addValue("turma", filtro.getTurma())
                .addValue("prioridade", filtro.getPrioridade()).addValue("perfil", filtro.getPerfil())
                .addValue("inicio", filtro.getInicio(), Types.DATE)
                .addValue("fim", filtro.getAgora().toLocalDate().plusDays(1), Types.DATE)
                .addValue("agora", filtro.getAgora(), Types.TIMESTAMP)
                .addValue("semana", filtro.getAgora().plusDays(7), Types.TIMESTAMP);
    }

    private static final String TURMAS = """
            WITH turmas AS (
                SELECT t.* FROM public."TURMAS" t
                WHERE (:tipo = 'admin'
                    OR (:tipo = 'aluno' AND :pessoa = ANY(t.id_alunos))
                    OR (:tipo = 'professor' AND (t.id_proprietario = :pessoa OR :pessoa = ANY(t.id_professores))))
                AND (:turma = 0 OR t.id = :turma)
            )
            """;

    public List<Map<String, Object>> listarTurmas(MapSqlParameterSource p) {
        return jdbc.queryForList(TURMAS + "SELECT id, nome_turma AS nome FROM turmas ORDER BY nome_turma, id", p);
    }

    private static final String CONTEUDOS_ALUNO = TURMAS + """
            , conteudos AS (
                SELECT c.id, c.id_turma, c.titulo, p.concluido_em,
                    p.concluido_em IS NULL AND EXISTS (
                        SELECT 1 FROM public."EVENTO_CONTEUDO" ec
                        JOIN public."EVENTOS" e ON e.id = ec.id_evento
                        WHERE ec.id_conteudo = c.id AND upper(e.status) = 'ATIVO'
                            AND EXISTS (SELECT 1 FROM turmas t WHERE t.id = e.id_turma)
                        HAVING max(e.data_hora) < :agora
                            AND (c.reaberto_em IS NULL OR c.reaberto_em AT TIME ZONE 'America/Sao_Paulo' <= max(e.data_hora))
                    ) AS atrasado
                FROM public."CONTEUDOS" c
                LEFT JOIN public."PROGRESSO_CONTEUDO" p ON p.id_conteudo = c.id AND p.id_aluno = :pessoa
                WHERE c.status = 'ativo' AND EXISTS (SELECT 1 FROM turmas t WHERE t.id = ANY(c.id_turma))
            )
            """;

    public Map<String, Object> resumoAluno(MapSqlParameterSource p) {
        return jdbc.queryForMap(CONTEUDOS_ALUNO + """
                SELECT (SELECT count(*) FROM turmas) AS turmas, count(*) AS conteudos,
                    count(concluido_em) AS concluidos,
                    count(*) FILTER (WHERE concluido_em IS NULL AND NOT atrasado) AS pendentes,
                    count(*) FILTER (WHERE atrasado) AS atrasados,
                    COALESCE(round(100.0 * count(concluido_em) / NULLIF(count(*), 0)), 0) AS progresso
                FROM conteudos
                """, p);
    }

    public List<Map<String, Object>> evolucaoAluno(MapSqlParameterSource p) {
        return jdbc.queryForList(CONTEUDOS_ALUNO + """
                SELECT to_char(concluido_em AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') AS nome,
                    count(*) AS quantidade FROM conteudos
                WHERE concluido_em IS NOT NULL
                    AND (:inicio IS NULL OR (concluido_em AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                    AND (concluido_em AT TIME ZONE 'America/Sao_Paulo')::date < :fim
                GROUP BY 1 ORDER BY 1
                """, p);
    }

    public List<Map<String, Object>> turmasAluno(MapSqlParameterSource p) {
        return jdbc.queryForList(CONTEUDOS_ALUNO + """
                SELECT t.id, t.nome_turma AS nome, count(c.id) AS conteudos,
                    count(c.concluido_em) AS concluidos
                FROM turmas t LEFT JOIN conteudos c ON t.id = ANY(c.id_turma)
                GROUP BY t.id, t.nome_turma ORDER BY t.nome_turma, t.id LIMIT 20
                """, p);
    }

    private static final String EVENTOS = TURMAS + """
            , eventos AS (
                SELECT e.*, t.nome_turma,
                    CASE WHEN e.data_hora > :agora THEN 'Futuros'
                         WHEN COALESCE(e.data_fim AT TIME ZONE 'America/Sao_Paulo', e.data_hora) >= :agora
                             THEN 'Em andamento' ELSE 'Passados' END AS situacao
                FROM public."EVENTOS" e JOIN turmas t ON t.id = e.id_turma
                WHERE upper(e.status) = 'ATIVO'
                    AND (:tipo <> 'professor' OR e.id_professor = :pessoa)
                    AND (:prioridade = 'todos' OR e.prioridade = :prioridade)
                    AND (:inicio IS NULL OR
                        CASE WHEN :tipo = 'aluno' THEN e.data_hora::date
                             ELSE (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date END >= :inicio)
                    AND (:inicio IS NULL OR
                        CASE WHEN :tipo = 'aluno' THEN e.data_hora::date
                             ELSE (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date END < :fim)
            )
            """;

    public Map<String, Object> resumoEventos(MapSqlParameterSource p) {
        return jdbc.queryForMap(EVENTOS + """
                SELECT count(*) AS eventos,
                    count(*) FILTER (WHERE situacao = 'Futuros') AS futuros,
                    count(*) FILTER (WHERE situacao = 'Passados') AS passados,
                    count(*) FILTER (WHERE situacao = 'Em andamento') AS andamento,
                    count(*) FILTER (WHERE data_hora >= :agora AND data_hora < :semana) AS proximos,
                    count(*) FILTER (WHERE prioridade = 'ALTA' AND situacao <> 'Passados') AS alta
                FROM eventos
                """, p);
    }

    public List<Map<String, Object>> eventosPorTurma(MapSqlParameterSource p) {
        return jdbc.queryForList(EVENTOS + """
                SELECT t.nome_turma AS nome, count(e.id) AS quantidade
                FROM turmas t LEFT JOIN eventos e ON e.id_turma = t.id
                GROUP BY t.id, t.nome_turma ORDER BY quantidade DESC, t.nome_turma, t.id LIMIT 10
                """, p);
    }

    public List<Map<String, Object>> prioridades(MapSqlParameterSource p) {
        return jdbc.queryForList(EVENTOS + """
                SELECT COALESCE(prioridade, 'Não informada') AS nome, count(*) AS quantidade
                FROM eventos GROUP BY prioridade ORDER BY prioridade
                """, p);
    }

    public List<Map<String, Object>> proximosEventos(MapSqlParameterSource p) {
        return jdbc.queryForList(EVENTOS + """
                SELECT id, titulo, id_turma, nome_turma,
                    to_char(data_hora, 'DD/MM/YYYY HH24:MI') AS data,
                    COALESCE(prioridade, 'Não informada') AS prioridade, situacao
                FROM eventos WHERE situacao <> 'Passados' ORDER BY data_hora, id LIMIT 10
                """, p);
    }
}
