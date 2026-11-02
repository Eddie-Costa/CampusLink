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

    private static final String CONTEUDOS = TURMAS + """
            , conteudos AS (
                SELECT c.*,
                    CASE WHEN c.status = 'ativo' THEN 'Ativos'
                         WHEN c.status = 'suspenso_denuncia' AND c.revisao_solicitada = true THEN 'Em análise'
                         WHEN c.status = 'suspenso_denuncia' THEN 'Suspensos sem revisão'
                         WHEN c.status = 'removido' THEN 'Removidos' ELSE 'Outros' END AS situacao
                FROM public."CONTEUDOS" c
                WHERE (:tipo = 'admin' OR c.id_professor = :pessoa)
                    AND (:turma = 0 OR EXISTS (SELECT 1 FROM turmas t WHERE t.id = ANY(c.id_turma)))
                    AND (:inicio IS NULL OR (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                    AND (:inicio IS NULL OR (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)
            )
            """;

    public Map<String, Object> resumoConteudos(MapSqlParameterSource p) {
        return jdbc.queryForMap(CONTEUDOS + """
                SELECT count(*) AS conteudos, count(*) FILTER (WHERE status = 'ativo') AS ativos,
                    count(*) FILTER (WHERE status = 'suspenso_denuncia') AS suspensos,
                    count(*) FILTER (WHERE situacao = 'Em análise') AS analise,
                    count(*) FILTER (WHERE status = 'removido') AS removidos
                FROM conteudos
                """, p);
    }

    public List<Map<String, Object>> situacaoConteudos(MapSqlParameterSource p) {
        return jdbc.queryForList(CONTEUDOS + "SELECT situacao AS nome, count(*) AS quantidade FROM conteudos GROUP BY situacao ORDER BY situacao", p);
    }

    public List<Map<String, Object>> conteudosPorTurma(MapSqlParameterSource p) {
        return jdbc.queryForList(CONTEUDOS + """
                SELECT t.nome_turma AS nome, count(c.id) AS quantidade
                FROM turmas t LEFT JOIN conteudos c ON t.id = ANY(c.id_turma)
                GROUP BY t.id, t.nome_turma ORDER BY quantidade DESC, t.nome_turma, t.id LIMIT 10
                """, p);
    }

    public List<Map<String, Object>> listarConteudos(MapSqlParameterSource p, boolean somenteAtencao) {
        return jdbc.queryForList(CONTEUDOS + """
                SELECT c.id, c.titulo, c.situacao,
                    to_char(c.created_at AT TIME ZONE 'America/Sao_Paulo', 'DD/MM/YYYY') AS data,
                    COALESCE(u.nome, 'Professor não disponível') AS professor,
                    COALESCE((SELECT string_agg(t.nome_turma, ', ' ORDER BY t.nome_turma)
                        FROM turmas t WHERE t.id = ANY(c.id_turma)), 'Sem turma no recorte') AS turmas,
                    (SELECT count(*) FROM public."DENUNCIAS" d WHERE d.id_conteudo = c.id) AS denuncias
                FROM conteudos c LEFT JOIN public."PROFESSORES" pr ON pr.id = c.id_professor
                LEFT JOIN public."USUARIOS" u ON u.id = pr.id_usuario
                WHERE (:atencao = false OR c.status = 'suspenso_denuncia'
                    OR EXISTS (SELECT 1 FROM public."DENUNCIAS" d WHERE d.id_conteudo = c.id AND d.status = 'pendente'))
                ORDER BY c.created_at DESC, c.id DESC LIMIT 10
                """, p.addValue("atencao", somenteAtencao));
    }

    public Map<String, Object> resumoTurmasProfessor(MapSqlParameterSource p) {
        return jdbc.queryForMap(TURMAS + """
                SELECT count(*) AS turmas,
                    count(*) FILTER (WHERE id_proprietario = :pessoa) AS gerenciadas,
                    count(*) FILTER (WHERE id_proprietario IS DISTINCT FROM :pessoa) AS participantes,
                    (SELECT count(DISTINCT aluno) FROM turmas t CROSS JOIN LATERAL unnest(t.id_alunos) AS aluno) AS alunos
                FROM turmas
                """, p);
    }

    public List<Map<String, Object>> turmasProfessor(MapSqlParameterSource p) {
        return jdbc.queryForList(CONTEUDOS + """
                SELECT t.id, t.nome_turma AS nome,
                    CASE WHEN t.id_proprietario = :pessoa THEN 'Responsável' ELSE 'Participante' END AS vinculo,
                    COALESCE(cardinality(t.id_alunos), 0) AS alunos,
                    (SELECT count(*) FROM conteudos c WHERE t.id = ANY(c.id_turma)) AS conteudos,
                    (SELECT count(*) FROM public."EVENTOS" e WHERE e.id_turma = t.id
                        AND e.id_professor = :pessoa AND upper(e.status) = 'ATIVO'
                        AND (:inicio IS NULL OR (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                        AND (:inicio IS NULL OR (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)) AS eventos
                FROM turmas t ORDER BY t.nome_turma, t.id LIMIT 20
                """, p);
    }

    public List<Map<String, Object>> eventosPorMes(MapSqlParameterSource p) {
        return jdbc.queryForList(EVENTOS + """
                SELECT to_char(created_at AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') AS nome,
                    count(*) AS quantidade FROM eventos GROUP BY 1 ORDER BY 1
                """, p);
    }

    private static final String USUARIOS = """
            WITH usuarios AS (
                SELECT id, nome, perfil, status, created_at FROM public."USUARIOS"
                WHERE (:perfil = 'todos' OR perfil = :perfil)
                    AND (:inicio IS NULL OR created_at >= :inicio)
                    AND (:inicio IS NULL OR created_at < :fim)
            )
            """;

    public Map<String, Object> resumoUsuarios(MapSqlParameterSource p) {
        return jdbc.queryForMap(USUARIOS + """
                SELECT count(*) AS usuarios,
                    count(*) FILTER (WHERE perfil = 'ALUNOS') AS alunos,
                    count(*) FILTER (WHERE perfil = 'PROFESSORES') AS professores,
                    count(*) FILTER (WHERE perfil = 'ADMINISTRADOR') AS administradores,
                    count(*) FILTER (WHERE status = true) AS ativos,
                    count(*) FILTER (WHERE status = false) AS inativos,
                    count(*) FILTER (WHERE status IS NULL) AS sem_status
                FROM usuarios
                """, p);
    }

    public List<Map<String, Object>> usuariosPorMes(MapSqlParameterSource p) {
        return jdbc.queryForList(USUARIOS + """
                SELECT to_char(created_at, 'YYYY-MM') AS nome, count(*) AS quantidade
                FROM usuarios WHERE created_at IS NOT NULL GROUP BY 1 ORDER BY 1
                """, p);
    }

    public List<Map<String, Object>> usuariosRecentes(MapSqlParameterSource p) {
        return jdbc.queryForList(USUARIOS + """
                SELECT id, nome, to_char(created_at, 'DD/MM/YYYY') AS data,
                    CASE perfil WHEN 'ALUNOS' THEN 'Aluno' WHEN 'PROFESSORES' THEN 'Professor'
                        WHEN 'ADMINISTRADOR' THEN 'Administrador' ELSE 'Outro' END AS perfil,
                    CASE WHEN status = true THEN 'Ativa' WHEN status = false THEN 'Inativa' ELSE 'Não informado' END AS situacao
                FROM usuarios ORDER BY created_at DESC NULLS LAST, id DESC LIMIT 10
                """, p);
    }

    private static final String DENUNCIAS = """
            WITH denuncias AS (
                SELECT id_conteudo, status, created_at FROM public."DENUNCIAS"
                WHERE (:inicio IS NULL OR (created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                    AND (:inicio IS NULL OR (created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)
            )
            """;

    public Map<String, Object> resumoDenuncias(MapSqlParameterSource p) {
        return jdbc.queryForMap(DENUNCIAS + """
                SELECT count(*) AS denuncias, count(DISTINCT id_conteudo) AS denunciados,
                    count(*) FILTER (WHERE status = 'pendente') AS pendentes,
                    count(*) FILTER (WHERE status IN ('analisada_liberada','analisada_removida')) AS resolvidas,
                    count(DISTINCT id_conteudo) FILTER (WHERE status = 'analisada_liberada') AS liberados
                FROM denuncias
                """, p);
    }

    public List<Map<String, Object>> denunciasPorSituacao(MapSqlParameterSource p) {
        return jdbc.queryForList(DENUNCIAS + """
                SELECT CASE status WHEN 'pendente' THEN 'Pendentes' WHEN 'analisada_liberada' THEN 'Analisadas: liberado'
                    WHEN 'analisada_removida' THEN 'Analisadas: removido' ELSE 'Outras' END AS nome,
                    count(*) AS quantidade FROM denuncias GROUP BY 1 ORDER BY 1
                """, p);
    }

    private static final String TURMAS_ADMIN = """
            WITH turmas AS (
                SELECT * FROM public."TURMAS"
                WHERE (:inicio IS NULL OR (created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                    AND (:inicio IS NULL OR (created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)
            )
            """;

    public Map<String, Object> resumoTurmasAdmin(MapSqlParameterSource p) {
        return jdbc.queryForMap(TURMAS_ADMIN + """
                SELECT count(*) AS turmas, count(DISTINCT id_proprietario) AS responsaveis,
                    (SELECT count(*) FROM public."EVENTOS" e
                        WHERE (:inicio IS NULL OR (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                            AND (:inicio IS NULL OR (e.created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)) AS eventos
                FROM turmas
                """, p);
    }

    public List<Map<String, Object>> turmasAdmin(MapSqlParameterSource p) {
        return jdbc.queryForList(TURMAS_ADMIN + """
                SELECT t.id, t.nome_turma AS nome, COALESCE(u.nome, 'Sem responsável') AS professor,
                    COALESCE(cardinality(t.id_alunos),0) AS alunos,
                    (SELECT count(*) FROM public."CONTEUDOS" c WHERE t.id = ANY(c.id_turma)
                        AND (:inicio IS NULL OR (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date >= :inicio)
                        AND (:inicio IS NULL OR (c.created_at AT TIME ZONE 'America/Sao_Paulo')::date < :fim)) AS conteudos
                FROM turmas t LEFT JOIN public."PROFESSORES" pr ON pr.id = t.id_proprietario
                LEFT JOIN public."USUARIOS" u ON u.id = pr.id_usuario
                ORDER BY t.nome_turma, t.id LIMIT 20
                """, p);
    }
}
