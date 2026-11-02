package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ArquivoDTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class ArquivoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ArquivoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void inserir(ArquivoDTO arquivo) throws SQLException {
        String sql = """
                INSERT INTO public."ARQUIVOS"
                (
                    "id_conteudo",
                    "bucket_id",
                    "storage_path",
                    "nome_original",
                    "mime_type",
                    "tamanho_bytes"
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try {
            jdbcTemplate.update(sql,
                    new Object[]{arquivo.getIdConteudo(), arquivo.getBucketId(), arquivo.getStoragePath(),
                            arquivo.getNomeOriginal(), arquivo.getMimeType(), arquivo.getTamanhoBytes().longValue()},
                    new int[]{Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT});
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public List<ArquivoDTO> listarTodos() throws SQLException {
        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                ORDER BY "created_at" DESC
                """;
        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarArquivo(rs));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public ArquivoDTO buscarPorId(Long id) throws SQLException {
        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? montarArquivo(rs) : null, id.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public ArquivoDTO buscarMaisRecentePorConteudo(Long idConteudo) throws SQLException {
        String sql = """
            SELECT *
            FROM public."ARQUIVOS"
            WHERE "id_conteudo" = ?
            ORDER BY "created_at" DESC
            LIMIT 1
            """;
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? montarArquivo(rs) : null, idConteudo.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    public void excluir(Long id) throws SQLException {
        String sql = """
                DELETE FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;
        try {
            jdbcTemplate.update(sql, id.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    private ArquivoDTO montarArquivo(ResultSet rs) throws SQLException {
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setId(rs.getLong("id"));

        long idConteudo = rs.getLong("id_conteudo");
        if (!rs.wasNull()) {
            arquivo.setIdConteudo(idConteudo);
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            arquivo.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        arquivo.setBucketId(rs.getString("bucket_id"));
        arquivo.setStoragePath(rs.getString("storage_path"));
        arquivo.setNomeOriginal(rs.getString("nome_original"));
        arquivo.setMimeType(rs.getString("mime_type"));
        arquivo.setTamanhoBytes(rs.getLong("tamanho_bytes"));

        Object uploadedBy = rs.getObject("uploaded_by");
        if (uploadedBy instanceof UUID uuid) {
            arquivo.setUploadedBy(uuid);
        }
        return arquivo;
    }

    // preserva o tratamento de erro e a compensacao do upload no service
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
