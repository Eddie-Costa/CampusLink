package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.turmaDAO;
import com.example.CampusLink.dto.TurmaDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TurmasControllerTest {

    @Test
    void turmas_deveRedirecionarAoLoginQuandoUsuarioNaoEstaNaSessao() throws SQLException {
        turmaDAO turmaDAO = mock(turmaDAO.class);
        professorDAO professorDAO = mock(professorDAO.class);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = criarController(turmaDAO, professorDAO).Turmas(model, new MockHttpSession());

        assertEquals("redirect:/login", view);
        assertFalse(model.containsAttribute("turmas"));
        verifyNoInteractions(turmaDAO, professorDAO);
    }

    @Test
    void turmas_deveListarTurmasDoProfessorAutenticado() throws SQLException {
        turmaDAO turmaDAO = mock(turmaDAO.class);
        professorDAO professorDAO = mock(professorDAO.class);
        when(professorDAO.buscarPorIDProfessor("professor@campuslink.invalid")).thenReturn("23");
        TurmaDTO turma = new TurmaDTO();
        turma.setId(7L);
        List<TurmaDTO> turmas = List.of(turma);
        when(turmaDAO.buscarTurmasDoUsuario("professor", "23")).thenReturn(turmas);
        MockHttpSession session = sessaoProfessor();
        session.setAttribute("usuarioLogado", new Object());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = criarController(turmaDAO, professorDAO).Turmas(model, session);

        assertEquals("Geral/turmas", view);
        assertSame(turmas, model.getAttribute("turmas"));
    }

    @Test
    void criarTurma_deveManterFormularioAbertoQuandoDadosForemInvalidos() throws SQLException {
        turmaDAO turmaDAO = mock(turmaDAO.class);
        professorDAO professorDAO = mock(professorDAO.class);
        TurmaDTO turma = new TurmaDTO();
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(turma, "turma");
        resultado.rejectValue("nomeTurma", "NomeObrigatorio", "Nome obrigatório");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = criarController(turmaDAO, professorDAO).criarTurma(turma, resultado,
                new MockHttpSession(), model, new RedirectAttributesModelMap());

        assertEquals("Geral/turmas", view);
        assertEquals(Boolean.TRUE, model.getAttribute("abrirModalCriarTurma"));
        verifyNoInteractions(turmaDAO, professorDAO);
    }

    @Test
    void criarTurma_deveImpedirAlunoDeCriarTurma() throws SQLException {
        turmaDAO turmaDAO = mock(turmaDAO.class);
        professorDAO professorDAO = mock(professorDAO.class);
        TurmaDTO turma = new TurmaDTO();
        turma.setNomeTurma("Nova turma");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("tipoUsuario", "aluno");

        String view = criarController(turmaDAO, professorDAO).criarTurma(turma,
                new BeanPropertyBindingResult(turma, "turma"), session,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("Geral/home", view);
        verify(professorDAO, never()).InsertTurmasIntoBD(anyString(), anyString(), anyString());
        verify(turmaDAO, never()).buscarUltimaTurmaPorProfessor(anyString());
    }

    @Test
    void criarTurma_deveCriarTurmaEVincularProfessor() throws SQLException {
        turmaDAO turmaDAO = mock(turmaDAO.class);
        professorDAO professorDAO = mock(professorDAO.class);
        when(professorDAO.buscarPorIDProfessor("professor@campuslink.invalid")).thenReturn("23");
        when(turmaDAO.buscarUltimaTurmaPorProfessor("23")).thenReturn("77");
        TurmaDTO turma = new TurmaDTO();
        turma.setNomeTurma("Engenharia de Software");
        turma.setDescricao("Turma de teste");
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = criarController(turmaDAO, professorDAO).criarTurma(turma,
                new BeanPropertyBindingResult(turma, "turma"),
                sessaoProfessor(), new ExtendedModelMap(), redirectAttributes);

        assertEquals("redirect:/turmas", view);
        assertEquals("Turma cadastrada com sucesso.", redirectAttributes.getFlashAttributes().get("mensagemSucesso"));
        verify(professorDAO).InsertTurmasIntoBD("Engenharia de Software", "Turma de teste", "23");
        verify(professorDAO).InsertProfessor_TurmaIntoBD("23", "77");
    }

    private turmasController criarController(turmaDAO turmaDAO, professorDAO professorDAO) {
        turmasController controller = new turmasController();
        ReflectionTestUtils.setField(controller, "turmaDAO", turmaDAO);
        ReflectionTestUtils.setField(controller, "professorDAO", professorDAO);
        ReflectionTestUtils.setField(controller, "alunoDAO", mock(alunoDAO.class));
        return controller;
    }

    private MockHttpSession sessaoProfessor() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("tipoUsuario", "professor");
        session.setAttribute("email2FA", "professor@campuslink.invalid");
        return session;
    }
}
