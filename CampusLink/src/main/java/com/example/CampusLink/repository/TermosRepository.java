package com.example.CampusLink.repository;

import com.example.CampusLink.dto.TermoAceiteDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.dto.TermoVersaoResumoDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class TermosRepository {

    private final JdbcTemplate jdbcTemplate;

    public TermosRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TermoVersaoDTO buscarVersaoAtual() {
        List<TermoVersaoDTO> versoes = jdbcTemplate.query("""
                SELECT id, versao, conteudo, hash_sha256, publicado_em
                FROM public."TERMOS_VERSOES"
                ORDER BY publicado_em DESC, id DESC
                LIMIT 1
                """, (rs, linha) -> new TermoVersaoDTO(
                rs.getLong("id"),
                rs.getString("versao"),
                rs.getString("conteudo"),
                rs.getString("hash_sha256"),
                rs.getObject("publicado_em", OffsetDateTime.class)));
        return versoes.isEmpty() ? null : versoes.getFirst();
    }

    public List<TermoVersaoResumoDTO> listarVersoes() {
        return jdbcTemplate.query("""
                SELECT t.id, t.versao, t.conteudo, t.hash_sha256, t.publicado_em,
                       (SELECT count(*) FROM public."CONSENTIMENTOS" c
                        WHERE c.id_termo = t.id AND c.revogado_em IS NULL) AS aceites
                FROM public."TERMOS_VERSOES" t
                ORDER BY t.publicado_em DESC, t.id DESC
                """, (rs, linha) -> new TermoVersaoResumoDTO(
                rs.getLong("id"),
                rs.getString("versao"),
                rs.getString("conteudo"),
                rs.getString("hash_sha256"),
                rs.getObject("publicado_em", OffsetDateTime.class),
                rs.getLong("aceites")));
    }

    public boolean existeVersaoOuConteudo(String versao, String hash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public."TERMOS_VERSOES" WHERE versao = ? OR hash_sha256 = ?)
                """, Boolean.class, versao, hash));
    }

    public void publicar(String versao, String conteudo, String hash) {
        jdbcTemplate.update("""
                INSERT INTO public."TERMOS_VERSOES" (versao, conteudo, hash_sha256)
                VALUES (?, ?, ?)
                """, versao, conteudo, hash);
    }

    public boolean possuiAceite(Long idUsuario, TermoVersaoDTO termo) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public."CONSENTIMENTOS"
                    WHERE id_usuario = ? AND tipo = 'termos_uso' AND id_termo = ? AND hash_sha256 = ?
                    AND revogado_em IS NULL
                )
                """, Boolean.class, idUsuario, termo.id(), termo.hashSha256()));
    }

    public TermoAceiteDTO buscarAceiteAtivo(Long idUsuario) {
        List<TermoAceiteDTO> aceites = jdbcTemplate.query("""
                SELECT t.versao, c.hash_sha256, c.aceito_em
                FROM public."CONSENTIMENTOS" c
                JOIN public."TERMOS_VERSOES" t ON t.id = c.id_termo
                WHERE c.id_usuario = ? AND c.tipo = 'termos_uso' AND c.revogado_em IS NULL
                ORDER BY t.publicado_em DESC, c.aceito_em DESC
                LIMIT 1
                """, (rs, linha) -> new TermoAceiteDTO(
                rs.getString("versao"),
                rs.getString("hash_sha256"),
                rs.getObject("aceito_em", OffsetDateTime.class)), idUsuario);
        return aceites.isEmpty() ? null : aceites.getFirst();
    }

    public int revogarAceites(Long idUsuario) {
        return jdbcTemplate.update("""
                UPDATE public."CONSENTIMENTOS" SET revogado_em = now()
                WHERE id_usuario = ? AND tipo = 'termos_uso' AND revogado_em IS NULL
                """, idUsuario);
    }

    public void registrarAceite(Long idUsuario, TermoVersaoDTO termo) {
        jdbcTemplate.update("""
                INSERT INTO public."CONSENTIMENTOS" (id_usuario, tipo, id_termo, hash_sha256)
                VALUES (?, 'termos_uso', ?, ?)
                ON CONFLICT (id_usuario, tipo, id_termo) WHERE revogado_em IS NULL DO NOTHING
                """, idUsuario, termo.id(), termo.hashSha256());
    }
}
