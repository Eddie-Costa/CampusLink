// banco de vinculos de materiais relaciona conteudos aos eventos
package com.example.CampusLink.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

// associacoes dos eventos esta classe inclui substitui e remove os vinculos dos materiais
@Repository
public class EventoConteudoRepository {

    private final JdbcTemplate jdbcTemplate;

    // preparar vinculos de evento e conteudo este construtor recebe o acesso ao banco
    public EventoConteudoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    // associar material ao evento este metodo grava um vinculo quando os dois identificadores existem
    public void associarConteudo(Long idEvento, Long idConteudo) throws SQLException {
        // exigir codigos do vinculo encerra a associacao quando falta evento ou conteudo
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

        // associar material ao evento grava o vinculo pelos codigos e converte falhas do banco
        try {
            jdbcTemplate.update(sql, idEvento, idConteudo);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }


    // associar varios materiais ao evento este metodo grava os vinculos em conjunto sem repetir identificadores
    public void associarConteudos(Long idEvento, List<Long> idsConteudos) throws SQLException {
        // ignorar associacao vazia encerra quando falta evento ou a lista de materiais esta vazia
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

        // associar materiais em lote grava codigos distintos e preenchidos e converte falhas do banco
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


    // substituir materiais do evento este metodo apaga os vinculos anteriores e inclui a nova selecao
    public void substituirConteudosDoEvento(Long idEvento, List<Long> idsConteudos) {
        // exigir evento da substituicao recusa trocar materiais quando falta o codigo do evento
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
        // aceitar lista substituta vazia encerra sem adicionar materiais quando a nova selecao esta vazia
        if (idsConteudos == null || idsConteudos.isEmpty()) {
            return;
        }

        List<Long> conteudos = idsConteudos.stream().filter(id -> id != null).distinct().toList();
        for (Long idConteudo : conteudos) {
            jdbcTemplate.update(sqlAdicionar, idEvento, idConteudo);
        }
    }


    // delete vinculos do evento este metodo apaga todas as associacoes de materiais do evento informado
    public void excluirAssociacoesDoEvento(Long idEvento) throws SQLException {
        // exigir evento para limpar encerra a remocao dos vinculos quando falta o codigo
        if (idEvento == null) {
            return;
        }

        String sql = """
                DELETE FROM public."EVENTO_CONTEUDO"
                WHERE id_evento = ?
                """;

        // delete associacoes do evento remove os vinculos do compromisso e converte falhas do banco
        try {
            jdbcTemplate.update(sql, idEvento);
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }


    // tratar erro dos vinculos do evento este metodo recupera a falha original ou cria uma falha de consulta
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
