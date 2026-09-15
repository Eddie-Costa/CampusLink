package com.example.CampusLink.dao;

import com.example.CampusLink.dto.ArquivoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ArquivoDAO {

    @Autowired
    private DataSource dataSource;

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

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            if (arquivo.getIdConteudo() != null) {
                stmt.setLong(1, arquivo.getIdConteudo());
            } else {
                stmt.setNull(1, Types.BIGINT);
            }

            stmt.setString(
                    2,
                    arquivo.getBucketId()
            );

            stmt.setString(
                    3,
                    arquivo.getStoragePath()
            );

            stmt.setString(
                    4,
                    arquivo.getNomeOriginal()
            );

            stmt.setString(
                    5,
                    arquivo.getMimeType()
            );

            stmt.setLong(
                    6,
                    arquivo.getTamanhoBytes()
            );

            stmt.executeUpdate();
        }
    }

    public List<ArquivoDTO> listarTodos() throws SQLException {

        List<ArquivoDTO> arquivos = new ArrayList<>();

        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                ORDER BY "created_at" DESC
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {
                arquivos.add(montarArquivo(rs));
            }
        }

        return arquivos;
    }

    public ArquivoDTO buscarPorId(Long id) throws SQLException {

        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return montarArquivo(rs);
                }
            }
        }

        return null;
    }
    public ArquivoDTO buscarMaisRecentePorConteudo(Long idConteudo) throws SQLException {

        String sql = """
            SELECT *
            FROM public."ARQUIVOS"
            WHERE "id_conteudo" = ?
            ORDER BY "created_at" DESC
            LIMIT 1
            """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, idConteudo);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return montarArquivo(rs);
                }
            }
        }

        return null;
    }

    public void excluir(Long id) throws SQLException {

        String sql = """
                DELETE FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;

        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, id);

            stmt.executeUpdate();
        }
    }

    private ArquivoDTO montarArquivo(ResultSet rs)
            throws SQLException {

        ArquivoDTO arquivo = new ArquivoDTO();

        arquivo.setId(
                rs.getLong("id")
        );

        long idConteudo =
                rs.getLong("id_conteudo");

        if (!rs.wasNull()) {
            arquivo.setIdConteudo(idConteudo);
        }

        Timestamp createdAt =
                rs.getTimestamp("created_at");

        if (createdAt != null) {

            arquivo.setCreatedAt(
                    createdAt
                            .toInstant()
                            .atOffset(ZoneOffset.UTC)
            );
        }

        arquivo.setBucketId(
                rs.getString("bucket_id")
        );
        arquivo.setStoragePath(
                rs.getString("storage_path")
        );
        arquivo.setNomeOriginal(
                rs.getString("nome_original")
        );
        arquivo.setMimeType(
                rs.getString("mime_type")
        );
        arquivo.setTamanhoBytes(
                rs.getLong("tamanho_bytes")
        );
        Object uploadedBy =
                rs.getObject("uploaded_by");

        if (uploadedBy instanceof UUID uuid) {
            arquivo.setUploadedBy(uuid);
        }
        return arquivo;
    }
}
