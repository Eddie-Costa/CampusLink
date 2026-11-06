// banco de arquivos centraliza o registro dos materiais enviados
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

// persistencia de anexos esta classe consulta e altera os dados que apontam para o armazenamento
@Repository
public class ArquivoRepository {

    private final JdbcTemplate jdbcTemplate;

    // preparar consulta de arquivos este construtor recebe o acesso ao banco
    public ArquivoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // cadastrar registro do arquivo este metodo grava local nome tipo tamanho e conteudo associado
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

        // registrar arquivo no banco salva local nome tipo e tamanho e converte falhas de gravacao
        try {
            jdbcTemplate.update(sql,
                    new Object[]{arquivo.getIdConteudo(), arquivo.getBucketId(), arquivo.getStoragePath(),
                            arquivo.getNomeOriginal(), arquivo.getMimeType(), arquivo.getTamanhoBytes().longValue()},
                    new int[]{Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT});
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // listar registros de arquivos este metodo consulta os materiais do mais recente ao mais antigo
    public List<ArquivoDTO> listarTodos() throws SQLException {
        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                ORDER BY "created_at" DESC
                """;
        // consultar todos os arquivos monta a lista de materiais e converte falhas do banco
        try {
            return jdbcTemplate.query(sql, (rs, linha) -> montarArquivo(rs));
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // buscar registro do arquivo este metodo consulta os dados pelo identificador
    public ArquivoDTO buscarPorId(Long id) throws SQLException {
        String sql = """
                SELECT *
                FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;
        // consultar arquivo pelo codigo busca o material ou retorna vazio e converte falhas do banco
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? montarArquivo(rs) : null, id.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // buscar ultimo arquivo do conteudo este metodo retorna o anexo mais recente do material
    public ArquivoDTO buscarMaisRecentePorConteudo(Long idConteudo) throws SQLException {
        String sql = """
            SELECT *
            FROM public."ARQUIVOS"
            WHERE "id_conteudo" = ?
            ORDER BY "created_at" DESC
            LIMIT 1
            """;
        // consultar material recente busca o ultimo arquivo do conteudo e converte falhas do banco
        try {
            return jdbcTemplate.query(sql, rs -> rs.next() ? montarArquivo(rs) : null, idConteudo.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // delete registro do arquivo este metodo apaga os dados do material no banco
    public void excluir(Long id) throws SQLException {
        String sql = """
                DELETE FROM public."ARQUIVOS"
                WHERE "id" = ?
                """;
        // delete registro de arquivo remove o material pelo codigo e converte falhas do banco
        try {
            jdbcTemplate.update(sql, id.longValue());
        } catch (DataAccessException e) {
            throw converterErro(e);
        }
    }

    // montar dados do arquivo este metodo converte a linha consultada incluindo data e autor do envio
    private ArquivoDTO montarArquivo(ResultSet rs) throws SQLException {
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setId(rs.getLong("id"));

        long idConteudo = rs.getLong("id_conteudo");
        // preencher vinculo do arquivo guarda o codigo do conteudo apenas quando o banco informa um valor
        if (!rs.wasNull()) {
            arquivo.setIdConteudo(idConteudo);
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        // preencher data do arquivo converte a data de criacao apenas quando ela existe
        if (createdAt != null) {
            arquivo.setCreatedAt(createdAt.toInstant().atOffset(ZoneOffset.UTC));
        }

        arquivo.setBucketId(rs.getString("bucket_id"));
        arquivo.setStoragePath(rs.getString("storage_path"));
        arquivo.setNomeOriginal(rs.getString("nome_original"));
        arquivo.setMimeType(rs.getString("mime_type"));
        arquivo.setTamanhoBytes(rs.getLong("tamanho_bytes"));

        Object uploadedBy = rs.getObject("uploaded_by");
        // preencher autor do arquivo aceita o identificador recebido quando ele usa o tipo esperado
        if (uploadedBy instanceof UUID uuid) {
            arquivo.setUploadedBy(uuid);
        }
        return arquivo;
    }


    // tratar erro do banco de arquivos este metodo recupera a falha original ou cria uma falha de consulta
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
