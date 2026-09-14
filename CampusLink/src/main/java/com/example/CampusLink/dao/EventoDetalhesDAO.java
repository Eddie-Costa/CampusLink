package com.example.CampusLink.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EventoDetalhesDAO {

    private final JdbcTemplate jdbcTemplate;


    // busca o nome da turma
    public String buscarNomeTurma(
            Long idTurma
    ) {

        if (idTurma == null) {
            return null;
        }

        String sql = """
                SELECT nome_turma
                FROM public."TURMAS"
                WHERE id = ?
                """;

        List<String> resultados =
                jdbcTemplate.query(
                        sql, (rs, rowNum) -> rs.getString("nome_turma"),
                        idTurma
                );

        if (resultados.isEmpty()) {
            return null;
        }

        return resultados.get(0);
    }


    // busca os conteudos ligados ao evento
    public List<String> buscarConteudosDoEvento(
            Long idEvento
    ) {

        if (idEvento == null) {
            return List.of();
        }

        String sql = """
                SELECT c.titulo
                FROM public."EVENTO_CONTEUDO" ec

                INNER JOIN public."CONTEUDOS" c
                    ON c.id = ec.id_conteudo

                WHERE ec.id_evento = ?

                ORDER BY c.titulo
                """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getString("titulo"),
                idEvento
        );
    }
}