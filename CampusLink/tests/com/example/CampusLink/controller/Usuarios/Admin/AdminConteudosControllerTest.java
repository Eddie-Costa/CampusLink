package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.*;
import com.example.CampusLink.service.*;
import com.example.CampusLink.support.AdminFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminConteudosControllerTest {
    @Mock ConteudoService conteudos;
    @Mock DenunciaService denuncias;
    @Mock RevisaoConteudoService revisao;
    @Mock ArquivoService arquivos;
    @Mock usuarioDAO usuarios;
    AdminConteudosController controller;
    MockHttpSession session;
    ExtendedModelMap model;
    RedirectAttributesModelMap flash;
    @BeforeEach void preparar() {
        controller = new AdminConteudosController(conteudos, denuncias, revisao, arquivos);
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarios);
        session = AdminFixture.sessao(); model = new ExtendedModelMap(); flash = new RedirectAttributesModelMap();
    }
    @Test void deveListarConteudosComDenunciasEArquivo() {
        var conteudo = new ConteudoDTO(); conteudo.setId(10L); var lista = List.of(conteudo);
        var relatos = List.of(new DenunciaDTO()); var arquivo = new ArquivoDTO();
        when(conteudos.listarParaAnaliseAdmin()).thenReturn(lista);
        when(denuncias.listarPorConteudo(10L)).thenReturn(relatos); when(arquivos.buscarMaisRecentePorConteudo(10L)).thenReturn(arquivo);
        assertEquals("Usuarios/Admin/conteudosDenunciados", controller.listarConteudos(session, model));
        assertSame(lista, model.get("conteudos"));
        assertEquals(Map.of(10L, relatos), model.get("denunciasPorConteudo"));
        assertEquals(Map.of(10L, arquivo), model.get("arquivosPorConteudo"));
    }
    @Test void deveExibirListagemVazia() {
        when(conteudos.listarParaAnaliseAdmin()).thenReturn(List.of());
        assertEquals("Usuarios/Admin/conteudosDenunciados", controller.listarConteudos(session, model));
        assertEquals(List.of(), model.get("conteudos")); verifyNoInteractions(denuncias, arquivos);
    }
    @Test void deveTratarFalhaAoCarregarConteudos() {
        when(conteudos.listarParaAnaliseAdmin()).thenThrow(new RuntimeException("falha simulada"));
        assertEquals("Usuarios/Admin/conteudosDenunciados", controller.listarConteudos(session, model));
        assertEquals(List.of(), model.get("conteudos")); assertNotNull(model.get("mensagemErro"));
    }
    @ParameterizedTest @CsvSource({"liberar,sucesso", "liberar,recusado", "liberar,erro", "reprovar,sucesso", "reprovar,recusado", "reprovar,erro"})
    void deveTratarResultadoDaModeracao(String acao, String estado) {
        var chamada = acao.equals("liberar") ? when(revisao.liberarConteudo(10L)) : when(revisao.reprovarConteudo(10L, "Motivo"));
        if (estado.equals("erro")) chamada.thenThrow(new RuntimeException("falha simulada")); else chamada.thenReturn(estado.equals("sucesso"));
        String retorno = acao.equals("liberar") ? controller.liberarConteudo(10L, session, flash) : controller.reprovarConteudo(10L, "Motivo", session, flash);
        assertEquals("redirect:/admin/conteudos-denunciados", retorno);
        assertNotNull(flash.getFlashAttributes().get(estado.equals("sucesso") ? "mensagemSucesso" : "mensagemErro"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void deveRecusarReprovacaoSemComentario(String motivo) {
        assertEquals("redirect:/admin/conteudos-denunciados", controller.reprovarConteudo(10L, motivo, session, flash));
        assertEquals("Informe o motivo da reprovação.", flash.getFlashAttributes().get("mensagemErro")); verifyNoInteractions(revisao);
    }
    static Stream<Arguments> acessos() {
        return Stream.of("listar", "liberar", "reprovar").flatMap(acao -> Stream.of("semSessao", "aluno", "emailDivergente").map(perfil -> Arguments.of(acao, perfil)));
    }
    @ParameterizedTest @MethodSource("acessos")
    void deveBloquearModeracaoSemAdministradorValido(String acao, String perfil) {
        if (perfil.equals("semSessao")) session = new MockHttpSession();
        else if (perfil.equals("aluno")) session.setAttribute("tipoUsuario", "aluno");
        else session.setAttribute("email2FA", "outro@example.com");
        String retorno = switch (acao) {
            case "listar" -> controller.listarConteudos(session, model);
            case "liberar" -> controller.liberarConteudo(10L, session, flash);
            default -> controller.reprovarConteudo(10L, "Motivo", session, flash);
        };
        assertEquals(perfil.equals("semSessao") ? AdminFixture.LOGIN : "redirect:/home", retorno);
        verifyNoInteractions(conteudos, denuncias, revisao, arquivos);
    }
}
