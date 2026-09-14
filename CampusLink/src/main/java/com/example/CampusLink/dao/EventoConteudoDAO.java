package com.example.CampusLink.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class EventoConteudoDAO {

    @Autowired
    private DataSource dataSource;

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

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idEvento);
            stmt.setLong(2, idConteudo);
            stmt.executeUpdate();
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

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            List<Long> conteudos = idsConteudos.stream().filter(id -> id != null).distinct().toList();

            for (Long idConteudo : conteudos) {

                stmt.setLong(1, idEvento);
                stmt.setLong(2, idConteudo);
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }


    // apaga as ligacoes do evento com os conteudos
    public void excluirAssociacoesDoEvento(Long idEvento
    ) throws SQLException {

        if (idEvento == null) {
            return;
        }

        String sql = """
                DELETE FROM public."EVENTO_CONTEUDO"
                WHERE id_evento = ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, idEvento);
            stmt.executeUpdate();
        }
    }
}