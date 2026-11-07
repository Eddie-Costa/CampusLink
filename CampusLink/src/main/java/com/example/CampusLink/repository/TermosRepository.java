// consultas dos termos de uso lgpd leem e gravam versoes com hash e os aceites dos usuarios
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.TermoAceiteDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.dto.TermoVersaoResumoDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

// controle dos dados dos termos esta classe acessa as tabelas TERMOS_VERSOES e CONSENTIMENTOS
@Repository
public class TermosRepository {

    private final JdbcTemplate jdbcTemplate;

    // preparar consultas dos termos este construtor recebe o acesso ao banco
    public TermosRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // buscar versao vigente este metodo retorna a versao publicada mais recente dos termos
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

    // listar historico dos termos este metodo retorna todas as versoes com a quantidade de aceites ativos de cada uma
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

    // verificar versao repetida este metodo informa se o numero ou o mesmo texto pelo hash ja foram publicados
    public boolean existeVersaoOuConteudo(String versao, String hash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM public."TERMOS_VERSOES" WHERE versao = ? OR hash_sha256 = ?)
                """, Boolean.class, versao, hash));
    }

    // publicar versao dos termos este metodo grava uma nova versao que o banco impede de alterar ou excluir depois
    public void publicar(String versao, String conteudo, String hash) {
        jdbcTemplate.update("""
                INSERT INTO public."TERMOS_VERSOES" (versao, conteudo, hash_sha256)
                VALUES (?, ?, ?)
                """, versao, conteudo, hash);
    }

    // conferir aceite do usuario este metodo verifica se ha aceite ativo da versao e do hash informados
    public boolean possuiAceite(Long idUsuario, TermoVersaoDTO termo) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public."CONSENTIMENTOS"
                    WHERE id_usuario = ? AND tipo = 'termos_uso' AND id_termo = ? AND hash_sha256 = ?
                    AND revogado_em IS NULL
                )
                """, Boolean.class, idUsuario, termo.id(), termo.hashSha256()));
    }

    // buscar aceite ativo este metodo retorna a versao o hash e a data do aceite mais recente nao revogado
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

    // revogar aceites do usuario este metodo preenche a data de revogacao e mantem o registro como historico
    public int revogarAceites(Long idUsuario) {
        return jdbcTemplate.update("""
                UPDATE public."CONSENTIMENTOS" SET revogado_em = now()
                WHERE id_usuario = ? AND tipo = 'termos_uso' AND revogado_em IS NULL
                """, idUsuario);
    }

    // gravar aceite dos termos este metodo registra o aceite com versao e hash e ignora aceite ativo repetido
    public void registrarAceite(Long idUsuario, TermoVersaoDTO termo) {
        jdbcTemplate.update("""
                INSERT INTO public."CONSENTIMENTOS" (id_usuario, tipo, id_termo, hash_sha256)
                VALUES (?, 'termos_uso', ?, ?)
                ON CONFLICT (id_usuario, tipo, id_termo) WHERE revogado_em IS NULL DO NOTHING
                """, idUsuario, termo.id(), termo.hashSha256());
    }
}
