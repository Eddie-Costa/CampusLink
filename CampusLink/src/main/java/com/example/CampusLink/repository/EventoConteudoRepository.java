package com.example.CampusLink.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

@Repository
public class EventoConteudoRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventoConteudoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // liga um conteudo ao evento
    public void associarConteudo(Long idEvento, Long idConteudo) throws SQLException {
        if (idEvento == null || idConteudo == null) {
            return;
        }

        String sql = """
                INSERT INTO public."EVENTO_CONTEUDO"
                (
                    id_evento,
                    id_conteudo
                )
                VALUES (?, ?)
                """;

        try {
            jdbcTemplate.update(sql, idEvento, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // liga varios conteudos ao mesmo evento
    public void associarConteudos(Long idEvento, List<Long> idsConteudos) throws SQLException {
        if (idEvento == null || idsConteudos == null || idsConteudos.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO public."EVENTO_CONTEUDO"
                (
                    id_evento,
                    id_conteudo
                )
                VALUES (?, ?)
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                List<Long> conteudos = idsConteudos.stream().filter(id -> id != null).distinct().toList();
                for (Long idConteudo : conteudos) {
                    stmt.setLong(1, idEvento);
                    stmt.setLong(2, idConteudo);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // participa da transacao de atualizacao aberta pelo service
    public void substituirConteudosDoEvento(Long idEvento, List<Long> idsConteudos) {
        if (idEvento == null) {
            throw new IllegalArgumentException("Informe o ID do evento");
        }

        String sqlExcluir = """
                DELETE FROM public."EVENTO_CONTEUDO"
                WHERE id_evento = ?
                """;

        String sqlAdicionar = """
                INSERT INTO public."EVENTO_CONTEUDO"
                    (id_evento, id_conteudo)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sqlExcluir, idEvento);
        if (idsConteudos == null || idsConteudos.isEmpty()) {
            return;
        }

        List<Long> conteudos = idsConteudos.stream().filter(id -> id != null).distinct().toList();
        for (Long idConteudo : conteudos) {
            jdbcTemplate.update(sqlAdicionar, idEvento, idConteudo);
        }
    }

    // apaga as ligacoes do evento com os conteudos
    public void excluirAssociacoesDoEvento(Long idEvento) throws SQLException {
        if (idEvento == null) {
            return;
        }

        String sql = """
                DELETE FROM public."EVENTO_CONTEUDO"
                WHERE id_evento = ?
                """;

        try {
            jdbcTemplate.update(sql, idEvento);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // preserva o contrato de erro usado pelo service
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
