package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.dto.Admin.*;
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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTurmasControllerTest {
    @Mock AdminTurmaDAO dao;
    @Mock usuarioDAO usuarios;
    AdminTurmasController controller;
    MockHttpSession session;
    ExtendedModelMap model;
    RedirectAttributesModelMap flash;
    @BeforeEach void preparar() {
        controller = new AdminTurmasController(dao); ReflectionTestUtils.setField(controller, "usuarioDAO", usuarios);
        session = AdminFixture.sessao(); model = new ExtendedModelMap(); flash = new RedirectAttributesModelMap();
    }
    private editarTurmaAdminDTO turma() {
        var dto = new editarTurmaAdminDTO(); dto.setId(1L); dto.setNomeTurma("Turma teste"); dto.setIdProprietario(2L); return dto;
    }
    private cadastrarTurmaAdminDTO cadastro() {
        var dto = new cadastrarTurmaAdminDTO(); dto.setNomeTurma("Turma teste"); dto.setIdProprietario(2L); return dto;
    }
    @Test void deveListarTurmas() throws Exception {
        var dto = new turmaAdminDTO(); dto.setId(1L); var lista = List.of(dto);
        when(dao.listarTurmas()).thenReturn(lista);
        assertEquals("Usuarios/Admin/turmasAdmin", controller.listarTurmas(session, model)); assertSame(lista, model.get("turmas"));
    }
    @Test void deveTratarErroNaListagem() throws Exception {
        when(dao.listarTurmas()).thenThrow(new SQLException("falha simulada"));
        assertEquals("Usuarios/Admin/turmasAdmin", controller.listarTurmas(session, model));
        assertEquals(List.of(), model.get("turmas")); assertNotNull(model.get("mensagemErro"));
    }
    @Test void deveAbrirCadastroComProfessoresDisponiveis() throws Exception {
        var lista = List.of(new usuarioAdminDTO()); when(dao.listarProfessoresAtivos()).thenReturn(lista);
        assertEquals("Usuarios/Admin/cadastrarTurmaAdmin", controller.exibirCadastroTurma(session, model));
        assertSame(lista, model.get("professores")); assertInstanceOf(cadastrarTurmaAdminDTO.class, model.get("turma"));
    }
    @Test void deveCadastrarTurma() throws Exception {
        var dto = cadastro();
        assertEquals("redirect:/admin/turmas", controller.cadastrarTurma(dto, new BeanPropertyBindingResult(dto, "turma"), session, model, flash));
        verify(dao).cadastrarTurma(dto); assertNotNull(flash.getFlashAttributes().get("mensagemSucesso"));
    }
    @Test void deveRecusarCadastroInvalido() throws Exception {
        var dto = cadastro(); var result = new BeanPropertyBindingResult(dto, "turma"); result.reject("invalido");
        assertEquals("Usuarios/Admin/cadastrarTurmaAdmin", controller.cadastrarTurma(dto, result, session, model, flash));
        verify(dao, never()).cadastrarTurma(any());
    }
    @Test void deveCarregarEdicao() throws Exception {
        var dto = turma(); when(dao.buscarTurmaPorId(1L)).thenReturn(dto);
        assertEquals("Usuarios/Admin/editarTurmaAdmin", controller.exibirEdicaoTurma(1L, session, model, flash));
        assertSame(dto, model.get("turma"));
    }
    @Test void deveEditarUsandoIdDaRota() throws Exception {
        var dto = turma(); dto.setId(999L); when(dao.buscarTurmaPorId(1L)).thenReturn(turma()); when(dao.atualizarTurma(dto)).thenReturn(true);
        assertEquals("redirect:/admin/turmas", controller.editarTurma(1L, dto, new BeanPropertyBindingResult(dto, "turma"), session, model, flash));
        assertEquals(1L, dto.getId()); verify(dao).atualizarTurma(dto); assertNotNull(flash.getFlashAttributes().get("mensagemSucesso"));
    }
    @Test void deveRecusarEdicaoInvalida() throws Exception {
        var dto = turma(); var result = new BeanPropertyBindingResult(dto, "turma"); result.reject("invalido");
        assertEquals("Usuarios/Admin/editarTurmaAdmin", controller.editarTurma(1L, dto, result, session, model, flash));
        verify(dao, never()).atualizarTurma(any());
    }
    @ParameterizedTest @ValueSource(strings = {"alunos", "professores"})
    void deveListarMembrosEDisponiveis(String tipo) throws Exception {
        var dto = turma(); when(dao.buscarTurmaPorId(1L)).thenReturn(dto);
        var membros = List.of(new usuarioAdminDTO()); var disponiveis = List.of(new usuarioAdminDTO());
        String retorno;
        if (tipo.equals("alunos")) {
            when(dao.listarAlunosDaTurma(1L)).thenReturn(membros); when(dao.listarAlunosDisponiveis(1L)).thenReturn(disponiveis);
            retorno = controller.listarAlunosDaTurma(1L, session, model, flash);
        } else {
            when(dao.listarProfessoresDaTurma(1L)).thenReturn(membros); when(dao.listarProfessoresDisponiveis(1L)).thenReturn(disponiveis);
            retorno = controller.listarProfessoresDaTurma(1L, session, model, flash);
        }
        assertEquals("Usuarios/Admin/" + tipo + "TurmaAdmin", retorno);
        assertSame(membros, model.get(tipo)); assertSame(disponiveis, model.get(tipo + "Disponiveis"));
    }
    private String vinculo(String acao) {
        return switch (acao) {
            case "adicionarAluno" -> controller.adicionarAlunoNaTurma(1L, 3L, session, flash);
            case "removerAluno" -> controller.removerAlunoDaTurma(1L, 3L, session, flash);
            case "adicionarProfessor" -> controller.adicionarProfessorNaTurma(1L, 3L, session, flash);
            default -> controller.removerProfessorDaTurma(1L, 3L, session, flash);
        };
    }
    static Stream<Arguments> vinculos() {
        return Stream.of("adicionarAluno", "removerAluno", "adicionarProfessor", "removerProfessor")
                .flatMap(acao -> Stream.of("sucesso", "recusado", "erro").map(estado -> Arguments.of(acao, estado)));
    }
    @ParameterizedTest @MethodSource("vinculos")
    void deveTratarResultadoDaAlteracaoDeVinculo(String acao, String estado) throws Exception {
        when(dao.buscarTurmaPorId(1L)).thenReturn(turma());
        org.mockito.stubbing.OngoingStubbing<Boolean> chamada = switch (acao) {
            case "adicionarAluno" -> when(dao.adicionarAlunoNaTurma(1L, 3L));
            case "removerAluno" -> when(dao.removerAlunoDaTurma(1L, 3L));
            case "adicionarProfessor" -> when(dao.adicionarProfessorNaTurma(1L, 3L));
            default -> when(dao.removerProfessorDaTurma(1L, 3L));
        };
        if (estado.equals("erro")) chamada.thenThrow(new SQLException("falha simulada")); else chamada.thenReturn(estado.equals("sucesso"));
        assertEquals("redirect:/admin/turmas/1/" + (acao.endsWith("Aluno") ? "alunos" : "professores"), vinculo(acao));
        assertNotNull(flash.getFlashAttributes().get(estado.equals("sucesso") ? "mensagemSucesso" : "mensagemErro"));
    }
    @Test void deveImpedirRemocaoDoProfessorResponsavel() throws Exception {
        var dto = turma(); dto.setIdProprietario(3L); when(dao.buscarTurmaPorId(1L)).thenReturn(dto);
        assertEquals("redirect:/admin/turmas/1/professores", vinculo("removerProfessor"));
        assertTrue(flash.getFlashAttributes().get("mensagemErro").toString().contains("responsável"));
        verify(dao, never()).removerProfessorDaTurma(anyLong(), anyLong());
    }
    @ParameterizedTest @ValueSource(strings = {"editarForm", "alunos", "professores", "adicionarAluno", "removerAluno", "adicionarProfessor", "removerProfessor"})
    void deveRecusarTurmaInexistente(String acao) throws Exception {
        assertEquals("redirect:/admin/turmas", executar(acao));
        assertEquals("Turma não encontrada.", flash.getFlashAttributes().get("mensagemErro"));
        verify(dao).buscarTurmaPorId(1L); verifyNoMoreInteractions(dao);
    }
    private String executar(String acao) {
        var novo = cadastro(); var edicao = turma();
        return switch (acao) {
            case "listar" -> controller.listarTurmas(session, model);
            case "formulario" -> controller.exibirCadastroTurma(session, model);
            case "cadastrar" -> controller.cadastrarTurma(novo, new BeanPropertyBindingResult(novo, "turma"), session, model, flash);
            case "editarForm" -> controller.exibirEdicaoTurma(1L, session, model, flash);
            case "editar" -> controller.editarTurma(1L, edicao, new BeanPropertyBindingResult(edicao, "turma"), session, model, flash);
            case "alunos" -> controller.listarAlunosDaTurma(1L, session, model, flash);
            case "professores" -> controller.listarProfessoresDaTurma(1L, session, model, flash);
            default -> vinculo(acao);
        };
    }
    static Stream<Arguments> acessos() {
        return Stream.of("listar", "formulario", "cadastrar", "editarForm", "editar", "alunos", "professores", "adicionarAluno", "removerAluno", "adicionarProfessor", "removerProfessor")
                .flatMap(acao -> Stream.of("semSessao", "aluno", "emailDivergente").map(perfil -> Arguments.of(acao, perfil)));
    }
    @ParameterizedTest @MethodSource("acessos")
    void deveBloquearTodasOperacoesSemAdministradorValido(String acao, String perfil) {
        if (perfil.equals("semSessao")) session = new MockHttpSession();
        else if (perfil.equals("aluno")) session.setAttribute("tipoUsuario", "aluno");
        else session.setAttribute("email2FA", "outro@example.com");
        assertEquals(perfil.equals("semSessao") ? AdminFixture.LOGIN : "redirect:/home", executar(acao));
        verifyNoInteractions(dao);
    }
}
