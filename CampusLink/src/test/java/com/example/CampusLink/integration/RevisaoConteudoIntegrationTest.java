package com.example.CampusLink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo de revisao de conteudo denunciado:
 * MeusConteudosController (professor) + AdminConteudosController (administrador).
 */
class RevisaoConteudoIntegrationTest extends BaseIntegracaoTest {

    private static final String LOGIN_ADMIN = "/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";
    private static final String PAINEL_ADMIN = "/admin/painel";
    private static final String LOGIN_PROFESSOR = "/loginProfessor";
    private static final String HOME = "/home";
    private static final String FILA_ADMIN = "/admin/conteudos-denunciados";
    private static final String MEUS_CONTEUDOS = "/meus-conteudos";

    private static final String SUSPENSO = "suspenso_denuncia";

    @Test
    void professorSolicitaRevisaoDeConteudoSuspenso() {
        Conta professor = criarProfessor();
        Conteudo conteudo = criarConteudo(professor, SUSPENSO, false);
        Navegador navegador = professorLogado(professor);

        ResponseEntity<String> resposta = navegador.post(
                MEUS_CONTEUDOS + "/" + conteudo.id() + "/solicitar-revisao", Map.of());

        assertRedireciona(resposta, MEUS_CONTEUDOS);
        assertTrue(revisaoFoiSolicitada(conteudo));
        assertTrue(navegador.get(MEUS_CONTEUDOS).getBody()
                .contains("Solicitação de análise enviada ao administrador."));
    }

    @Test
    void filaDoAdminMostraApenasConteudosComRevisaoSolicitada() {
        Conta professor = criarProfessor();
        Conteudo comPedido = criarConteudo(professor, SUSPENSO, true);
        Conteudo semPedido = criarConteudo(professor, SUSPENSO, false);

        ResponseEntity<String> resposta = adminLogado().get(FILA_ADMIN);
        String fila = resposta.getBody();

        assertEquals(200, resposta.getStatusCode().value());
        assertTrue(fila.contains(comPedido.titulo()));
        assertFalse(fila.contains(semPedido.titulo()));
    }

    @Test
    void professorRemoveConteudoEAlteracaoFicaPersistida() {
        Conta professor = criarProfessor();
        Conteudo conteudo = criarConteudo(professor, "ativo", false);
        Navegador navegador = professorLogado(professor);

        ResponseEntity<String> resposta = navegador.post(
                MEUS_CONTEUDOS + "/" + conteudo.id() + "/remover", Map.of());

        assertRedireciona(resposta, MEUS_CONTEUDOS);
        assertEquals("removido", statusDoConteudo(conteudo));
        assertFalse(revisaoFoiSolicitada(conteudo));
        assertTrue(navegador.get(MEUS_CONTEUDOS).getBody()
                .contains("Conteúdo removido com sucesso."));
    }

    @Test
    void adminLiberaConteudoQueVoltaAFicarAtivo() {
        Conta professor = criarProfessor();
        Conteudo conteudo = criarConteudo(professor, SUSPENSO, true);
        criarDenunciaPendente(conteudo, professor);
        Navegador admin = adminLogado();

        ResponseEntity<String> resposta = admin.post(
                FILA_ADMIN + "/" + conteudo.id() + "/liberar", Map.of());

        assertRedireciona(resposta, FILA_ADMIN);
        assertEquals("ativo", statusDoConteudo(conteudo));
        assertFalse(revisaoFoiSolicitada(conteudo));
        assertEquals("analisada_liberada", statusDaDenuncia(conteudo));
        assertTrue(admin.get(FILA_ADMIN).getBody().contains("Conteúdo liberado novamente."));
    }

    @Test
    void adminReprovaConteudoEOProfessorVeOMotivo() {
        Conta professor = criarProfessor();
        Conteudo conteudo = criarConteudo(professor, SUSPENSO, true);
        criarDenunciaPendente(conteudo, professor);
        String motivo = "Material nao condiz com a disciplina";

        ResponseEntity<String> resposta = adminLogado().post(
                FILA_ADMIN + "/" + conteudo.id() + "/reprovar", Map.of("comentarioAdmin", motivo));

        assertRedireciona(resposta, FILA_ADMIN);
        assertEquals("removido", statusDoConteudo(conteudo));
        assertEquals(motivo, comentarioDoAdmin(conteudo));
        assertEquals("analisada_removida", statusDaDenuncia(conteudo));
        assertTrue(professorLogado(professor).get(MEUS_CONTEUDOS).getBody().contains(motivo));
    }

    @Test
    void reprovarSemMotivoNaoAlteraOConteudo() {
        Conta professor = criarProfessor();
        Conteudo conteudo = criarConteudo(professor, SUSPENSO, true);
        Navegador admin = adminLogado();

        ResponseEntity<String> resposta = admin.post(
                FILA_ADMIN + "/" + conteudo.id() + "/reprovar", Map.of("comentarioAdmin", "   "));

        assertRedireciona(resposta, FILA_ADMIN);
        assertEquals(SUSPENSO, statusDoConteudo(conteudo));
        assertNull(comentarioDoAdmin(conteudo));
        assertTrue(admin.get(FILA_ADMIN).getBody().contains("Informe o motivo da reprovação."));
    }

    @Test
    void visitanteSemSessaoEhEnviadoParaOLoginDoAdmin() {
        ResponseEntity<String> resposta = new Navegador().get(FILA_ADMIN);

        assertRedireciona(resposta, LOGIN_ADMIN);
    }

    @Test
    void professorNaoAcessaFilaDoAdmin() {
        Navegador professor = professorLogado(criarProfessor());

        ResponseEntity<String> resposta = professor.get(FILA_ADMIN);

        assertRedireciona(resposta, HOME);
    }

    @Test
    void getEmEndpointDeRemocaoRetornaMetodoNaoPermitido() {
        ResponseEntity<String> resposta = new Navegador()
                .get(MEUS_CONTEUDOS + "/1/remover");

        assertEquals(405, resposta.getStatusCode().value());
    }

    @Test
    void getEmEndpointDeLiberacaoRetornaMetodoNaoPermitido() {
        ResponseEntity<String> resposta = new Navegador()
                .get(FILA_ADMIN + "/1/liberar");

        assertEquals(405, resposta.getStatusCode().value());
    }

    @Test
    void reprovarSemParametroDeComentarioRetornaBadRequest() {
        Navegador admin = adminLogado();

        ResponseEntity<String> resposta = admin.post(
                FILA_ADMIN + "/1/reprovar", Map.of());

        assertEquals(400, resposta.getStatusCode().value());
    }

    @Test
    void idDeConteudoInvalidoRetornaBadRequest() {
        Navegador professor = professorLogado(criarProfessor());

        ResponseEntity<String> resposta = professor.post(
                MEUS_CONTEUDOS + "/id-invalido/remover", Map.of());

        assertEquals(400, resposta.getStatusCode().value());
    }

    @Test
    void postSemTokenCsrfEhRejeitado() {
        ResponseEntity<String> resposta = new Navegador()
                .postSemCsrf(FILA_ADMIN + "/1/liberar", Map.of());

        assertEquals(403, resposta.getStatusCode().value());
    }

    // ---------- apoio ----------

    private Navegador adminLogado() {
        Navegador navegador = new Navegador();
        entrar(navegador, LOGIN_ADMIN, criarAdmin(), PAINEL_ADMIN);
        return navegador;
    }

    private Navegador professorLogado(Conta professor) {
        Navegador navegador = new Navegador();
        entrar(navegador, LOGIN_PROFESSOR, professor, HOME);
        return navegador;
    }

    private String statusDoConteudo(Conteudo conteudo) {
        return jdbc.queryForObject(
                "SELECT status FROM public.\"CONTEUDOS\" WHERE id = ?", String.class, conteudo.id());
    }

    private String comentarioDoAdmin(Conteudo conteudo) {
        return jdbc.queryForObject(
                "SELECT comentario_admin FROM public.\"CONTEUDOS\" WHERE id = ?", String.class, conteudo.id());
    }

    private boolean revisaoFoiSolicitada(Conteudo conteudo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT revisao_solicitada FROM public.\"CONTEUDOS\" WHERE id = ?", Boolean.class, conteudo.id()));
    }

    private String statusDaDenuncia(Conteudo conteudo) {
        return jdbc.queryForObject(
                "SELECT status FROM public.\"DENUNCIAS\" WHERE id_conteudo = ?", String.class, conteudo.id());
    }
}