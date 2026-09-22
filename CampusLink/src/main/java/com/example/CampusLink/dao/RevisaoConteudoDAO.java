package com.example.CampusLink.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Repository
public class RevisaoConteudoDAO {

    @Autowired
    private DataSource dataSource;

    public boolean liberarConteudo(Long idConteudo) throws SQLException {

        if (idConteudo == null) {
            return false;
        }

        String sqlConteudo = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'ativo',
                    "comentario_admin" = NULL,
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                AND "status" = 'suspenso_denuncia'
                AND "revisao_solicitada" = true
                """;

        String sqlDenuncias = """
                UPDATE public."DENUNCIAS"
                SET "status" = 'analisada_liberada'
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement stmt = conn.prepareStatement(sqlConteudo)) {
                    stmt.setLong(1, idConteudo);

                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlDenuncias)) {
                    stmt.setLong(1, idConteudo);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean reprovarConteudo(Long idConteudo, String comentarioAdmin) throws SQLException {

        if (idConteudo == null || comentarioAdmin == null || comentarioAdmin.isBlank()) {
            return false;
        }

        String sqlConteudo = """
                UPDATE public."CONTEUDOS"
                SET "status" = 'removido',
                    "comentario_admin" = ?,
                    "revisao_solicitada" = false,
                    "revisao_solicitada_em" = NULL
                WHERE "id" = ?
                AND "status" = 'suspenso_denuncia'
                AND "revisao_solicitada" = true
                """;

        String sqlDenuncias = """
                UPDATE public."DENUNCIAS"
                SET "status" = 'analisada_removida'
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        try (Connection conn = dataSource.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement stmt = conn.prepareStatement(sqlConteudo)) {
                    stmt.setString(1, comentarioAdmin.trim());
                    stmt.setLong(2, idConteudo);

                    if (stmt.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sqlDenuncias)) {
                    stmt.setLong(1, idConteudo);
                    stmt.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}