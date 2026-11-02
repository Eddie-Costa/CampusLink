package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DenunciaDTO;
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
public class DenunciaRepository {

    private final JdbcTemplate jdbcTemplate;

    public DenunciaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void inserir(DenunciaDTO denuncia) throws SQLException {

        String sql = """
                INSERT INTO public."DENUNCIAS"
                ( "id_conteudo", "id_aluno", "id_professor", "motivo")
                VALUES (?, ?, ?, ?)
                RETURNING "id", "status", "created_at"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {
                stmt.setLong(1, denuncia.getIdConteudo());
                stmt.setLong(2, denuncia.getIdAluno());
                stmt.setLong(3, denuncia.getIdProfessor());
                stmt.setString(4, denuncia.getMotivo());

                try (ResultSet rs = stmt.executeQuery()) {

                    if (rs.next()) {

                        denuncia.setId(rs.getLong("id"));
                        denuncia.setStatus(rs.getString("status"));
                        Timestamp createdAt = rs.getTimestamp("created_at");

                        if (createdAt != null) {
                            denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public boolean alunoJaDenunciou(Long idConteudo, Long idAluno) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "id_aluno" = ?
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Boolean>) stmt -> {

                stmt.setLong(1, idConteudo);
                stmt.setLong(2, idAluno);

                try (ResultSet rs = stmt.executeQuery()) {

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

    public int contarDenunciasPendentes(Long idConteudo) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."DENUNCIAS"
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                stmt.setLong(1, idConteudo);

                try (ResultSet rs = stmt.executeQuery()) {

                    if (rs.next()) {
                        return rs.getInt("quantidade");
                    }
                }
                return 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public int contarAlunosDaTurma(
            Long idTurma
    ) throws SQLException {

        String sql = """
                SELECT COUNT(DISTINCT "id_aluno") AS quantidade
                FROM public."ALUNO_TURMA"
                WHERE "id_turma" = ?
                """;

        try {
            return jdbcTemplate.execute(sql, (PreparedStatementCallback<Integer>) stmt -> {

                stmt.setLong(1, idTurma);

                try (ResultSet rs = stmt.executeQuery()) {

                    if (rs.next()) {
                        return rs.getInt("quantidade");
                    }
                }
                return 0;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<String> listarNomesAlunosPorConteudo(Long idConteudo) throws SQLException {

        List<String> alunos = new ArrayList<>();

        String sql = """
                SELECT DISTINCT u."nome"
                FROM public."DENUNCIAS" d
                INNER JOIN public."ALUNOS" a
                    ON a."id" = d."id_aluno"
                INNER JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE d."id_conteudo" = ?
                ORDER BY u."nome"
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idConteudo);

                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        alunos.add(rs.getString("nome"));
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return alunos;
    }

    public List<DenunciaDTO> listarPorConteudo(Long idConteudo) throws SQLException {

        List<DenunciaDTO> denuncias = new ArrayList<>();

        String sql = """
                SELECT d.*, u."nome" AS nome_aluno
                FROM public."DENUNCIAS" d
                LEFT JOIN public."ALUNOS" a
                    ON a."id" = d."id_aluno"
                LEFT JOIN public."USUARIOS" u
                    ON u."id" = a."id_usuario"
                WHERE d."id_conteudo" = ?
                ORDER BY d."created_at" DESC
                """;

        try {
            jdbcTemplate.execute(sql, (PreparedStatementCallback<Void>) stmt -> {

                stmt.setLong(1, idConteudo);

                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {

                        DenunciaDTO denuncia = new DenunciaDTO();
                        denuncia.setId(rs.getLong("id"));
                        denuncia.setIdConteudo(rs.getLong("id_conteudo"));
                        denuncia.setIdAluno(rs.getLong("id_aluno"));
                        denuncia.setIdProfessor(rs.getLong("id_professor"));
                        denuncia.setMotivo(rs.getString("motivo"));
                        denuncia.setStatus(rs.getString("status"));
                        denuncia.setNomeAluno(rs.getString("nome_aluno"));
                        Timestamp createdAt = rs.getTimestamp("created_at");

                        if (createdAt != null) {
                            denuncia.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
                        }

                        denuncias.add(denuncia);
                    }
                }
                return null;
            });
        } catch (DataAccessException e) {
            throw converterErro(e);
        }

        return denuncias;
    }

    public void atualizarStatusPorConteudo(Long idConteudo, String status) throws SQLException {

        String sql = """
                UPDATE public."DENUNCIAS"
                SET "status" = ?
                WHERE "id_conteudo" = ?
                AND "status" = 'pendente'
                """;

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
