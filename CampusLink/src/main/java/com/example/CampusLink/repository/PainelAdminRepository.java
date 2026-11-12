// numeros do painel administrativo reunem totais e pendencias em uma unica consulta
package com.example.CampusLink.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

// consulta do painel esta classe conta usuarios turmas conteudos eventos e itens que aguardam o administrador
@Repository
public class PainelAdminRepository {

    private static final String SQL_RESUMO = """
            SELECT
                (SELECT count(*) FROM public."USUARIOS" WHERE status = true) AS usuarios_ativos,
                (SELECT count(*) FROM public."TURMAS") AS turmas,
                (SELECT count(*) FROM public."CONTEUDOS" WHERE status = 'ativo') AS conteudos_ativos,
                (SELECT count(*) FROM public."EVENTOS" WHERE upper(status) = 'ATIVO') AS eventos_ativos,
                (SELECT count(*) FROM public."CHAMADOS" WHERE status = 'aberto') AS chamados_abertos,
                (SELECT count(*) FROM public."CONTEUDOS"
                    WHERE status = 'suspenso_denuncia' AND revisao_solicitada = true) AS analises_pendentes,
                (SELECT count(*) FROM public."DOMINIOS" WHERE status = 'pendente') AS dominios_pendentes
            """;

    private final JdbcTemplate jdbcTemplate;

    // preparar consulta do painel este construtor recebe o acesso ao banco
    public PainelAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // resumir painel este metodo devolve cada numero pelo nome da coluna
    public Map<String, Object> resumir() {
        return jdbcTemplate.queryForMap(SQL_RESUMO);
    }
}
