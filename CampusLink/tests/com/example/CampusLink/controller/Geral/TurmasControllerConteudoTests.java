package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurmasControllerConteudoTests {

    @Test
    void alunoLogadoNaoPodeCriarConteudo() throws Exception {
        turmasController controller = new turmasController();
        ConteudoService conteudoService = mock(ConteudoService.class);
        professorDAO professores = mock(professorDAO.class);
        usuarioDAO usuarios = mock(usuarioDAO.class);
        ReflectionTestUtils.setField(controller, "conteudoService", conteudoService);
        ReflectionTestUtils.setField(controller, "professorDAO", professores);
        ReflectionTestUtils.setField(controller, "usuarioDAO", usuarios);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        session.setAttribute("tipoUsuario", "aluno");
        ConteudoDTO conteudo = new ConteudoDTO();
        BindingResult resultadoValidacao = new BeanPropertyBindingResult(conteudo, "conteudo");

        String destino = controller.criarConteudo(
                12L, conteudo, resultadoValidacao, null, session, new ExtendedModelMap());

        assertEquals("redirect:/turmas/12", destino);
        verify(conteudoService, never()).criar(any(ConteudoDTO.class), any());
        verify(professores, never()).buscarPorIDProfessor(anyString());
    }

    @Test
    void professorLogadoCriaConteudoVinculadoATurmaEAoSeuId() throws Exception {
        turmasController controller = new turmasController();
        ConteudoService conteudoService = mock(ConteudoService.class);
        professorDAO professores = mock(professorDAO.class);
        ReflectionTestUtils.setField(controller, "conteudoService", conteudoService);
        ReflectionTestUtils.setField(controller, "professorDAO", professores);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        session.setAttribute("tipoUsuario", "professor");
        session.setAttribute("email2FA", "professor@example.com");
        ConteudoDTO conteudo = new ConteudoDTO();
        BindingResult resultadoValidacao = new BeanPropertyBindingResult(conteudo, "conteudo");
        when(professores.buscarPorIDProfessor("professor@example.com")).thenReturn("34");

        String destino = controller.criarConteudo(
                12L, conteudo, resultadoValidacao, null, session, new ExtendedModelMap());

        assertEquals("redirect:/turmas/12", destino);
        assertEquals(12L, conteudo.getIdTurma());
        assertEquals(34L, conteudo.getIdProfessor());
        verify(conteudoService).criar(conteudo, null);
        verify(professores).buscarPorIDProfessor("professor@example.com");
    }

    @Test
    void professorLogadoEditaConteudoUsandoIdDaRota() throws Exception {
        turmasController controller = new turmasController();
        ConteudoService conteudoService = mock(ConteudoService.class);
        ReflectionTestUtils.setField(controller, "conteudoService", conteudoService);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        session.setAttribute("tipoUsuario", "professor");
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(999L);
        BindingResult resultadoValidacao = new BeanPropertyBindingResult(conteudo, "conteudo");

        String destino = controller.editarConteudo(
                12L, 45L, conteudo, resultadoValidacao, null, session, new ExtendedModelMap());

        assertEquals("redirect:/turmas/12", destino);
        assertEquals(45L, conteudo.getId());
        verify(conteudoService).atualizar(conteudo);
    }
}