package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DominioDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DominioRepository {

    private final JdbcTemplate jdbcTemplate;

    public DominioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------- professor ----------

    public void inserir(DominioDTO dominio) throws SQLException {

        String sql = """
                INSERT INTO public."DOMINIOS" ("dominio", "id_professor", "justificativa")
                VALUES (?, ?, ?)
                RETURNING "id", "status", "created_at"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setString(1, dominio.getDominio());
                stmt.setLong(2, dominio.getIdProfessor());
                stmt.setString(3, dominio.getJustificativa());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dominio.setId(rs.getLong("id"));
                        dominio.setStatus(rs.getString("status"));

                        Timestamp createdAt = rs.getTimestamp("created_at");
                        if (createdAt != null) {
                            dominio.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<DominioDTO> listarPorProfessor(Long idProfessor) throws SQLException {

        String sql = """
                SELECT *
                FROM public."DOMINIOS"
                WHERE "id_professor" = ?
                ORDER BY "created_at" DESC
                """;

        List<DominioDTO> dominios = new ArrayList<>();

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idProfessor);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        dominios.add(montar(rs));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return dominios;
    }

    // ---------- admin ----------

    public List<DominioDTO> listarPendentes() throws SQLException {

        String sql = """
                SELECT d.*, u."nome" AS nome_professor
                FROM public."DOMINIOS" d
                LEFT JOIN public."PROFESSORES" p ON p."id" = d."id_professor"
                LEFT JOIN public."USUARIOS" u ON u."id" = p."id_usuario"
                WHERE d."status" = 'pendente'
                ORDER BY d."created_at" ASC
                """;

        List<DominioDTO> dominios = new ArrayList<>();

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        DominioDTO dominio = montar(rs);
                        dominio.setNomeProfessor(rs.getString("nome_professor"));
                        dominios.add(dominio);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return dominios;
    }

    public List<DominioDTO> listarAprovados() throws SQLException {

        String sql = """
                SELECT *
                FROM public."DOMINIOS"
                WHERE "status" = 'aprovado'
                ORDER BY "dominio" ASC
                """;

        List<DominioDTO> dominios = new ArrayList<>();

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        dominios.add(montar(rs));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return dominios;
    }

    // só analisa solicitações que ainda estão pendentes
    public boolean analisar(Long id, String novoStatus, String comentarioAdmin) throws SQLException {

        String sql = """
                UPDATE public."DOMINIOS"
                SET "status" = ?,
                    "comentario_admin" = ?,
                    "analisado_em" = now()
                WHERE "id" = ?
                AND "status" = 'pendente'
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, novoStatus);
                stmt.setString(2, comentarioAdmin);
                stmt.setLong(3, id);

                return stmt.executeUpdate() > 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // ---------- consultas usadas pela validação ----------

    // true se já existe pedido pendente ou aprovado para esse domínio
    public boolean existePendenteOuAprovado(String dominio) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."DOMINIOS"
                WHERE lower("dominio") = lower(?)
                AND "status" IN ('pendente', 'aprovado')
                LIMIT 1
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, dominio);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // true se o host é um domínio aprovado ou subdomínio dele
    // ex: aprovado "exemplo.com" libera "exemplo.com" e "www.exemplo.com"
    public boolean hostAutorizado(String host) throws SQLException {

        String sql = """
                SELECT 1
                FROM public."DOMINIOS"
                WHERE "status" = 'aprovado'
                AND (
                    lower(?) = lower("dominio")
                    OR right(lower(?), length("dominio") + 1) = '.' || lower("dominio")
                )
                LIMIT 1
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setString(1, host);
                stmt.setString(2, host);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // ---------- util ----------

    private DominioDTO montar(ResultSet rs) throws SQLException {

        DominioDTO dominio = new DominioDTO();

        dominio.setId(rs.getLong("id"));
        dominio.setDominio(rs.getString("dominio"));
        dominio.setStatus(rs.getString("status"));

        long idProfessor = rs.getLong("id_professor");
        dominio.setIdProfessor(rs.wasNull() ? null : idProfessor);

        dominio.setJustificativa(rs.getString("justificativa"));
        dominio.setComentarioAdmin(rs.getString("comentario_admin"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            dominio.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        Timestamp analisadoEm = rs.getTimestamp("analisado_em");
        if (analisadoEm != null) {
            dominio.setAnalisadoEm(analisadoEm.toInstant().atOffset(ZoneOffset.UTC));
        }

        return dominio;
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
