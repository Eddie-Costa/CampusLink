package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.service.DenunciaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DenunciaControllerTest {
    @Mock DenunciaService service;
    @Mock alunoDAO alunos;
    @Mock turmaDAO turmas;
    @Mock conteudoDAO conteudos;
    @Mock usuarioDAO usuarios;
    DenunciaController controller; MockHttpSession session; RedirectAttributesModelMap flash;
    @BeforeEach void preparar() {
        controller=new DenunciaController(service,alunos,turmas,conteudos); ReflectionTestUtils.setField(controller,"usuarioDAO",usuarios);
        session=new MockHttpSession(); session.setAttribute("usuarioLogado",new Object()); session.setAttribute("tipoUsuario","aluno"); session.setAttribute("email2FA","aluno@example.com"); flash=new RedirectAttributesModelMap();
    }
    private void alunoDaTurma() throws Exception {
        when(alunos.buscarPorIDAluno("aluno@example.com")).thenReturn("10");
        var turma=new TurmaDTO(); turma.setId(30L); when(turmas.buscarTurmasDoUsuario("aluno","10")).thenReturn(List.of(turma));
    }
    @ParameterizedTest @CsvSource({"sucesso,mensagemSucesso","suspenso,mensagemSucesso","duplicada,mensagemErro","motivo_vazio,mensagemErro","conteudo_indisponivel,mensagemErro"})
    void deveApresentarResultadoDoRegistro(String resultado,String chave) throws Exception {
        alunoDaTurma(); when(conteudos.conteudoPertenceTurma(40L,30L)).thenReturn(true); when(service.registrarDenuncia(40L,10L,30L,"Motivo")).thenReturn(resultado);
        assertEquals("redirect:/turmas/30",controller.denunciarConteudo(30L,40L,"Motivo",session,flash));
        assertNotNull(flash.getFlashAttributes().get(chave)); verify(service).registrarDenuncia(40L,10L,30L,"Motivo");
    }
    @Test void deveImpedirDenunciaForaDaTurmaDoAluno() throws Exception {
        when(alunos.buscarPorIDAluno("aluno@example.com")).thenReturn("10");
        assertEquals("redirect:/turmas",controller.denunciarConteudo(30L,40L,"Motivo",session,flash));
        assertEquals("Você não participa desta turma.",flash.getFlashAttributes().get("mensagemErro")); verifyNoInteractions(service,conteudos);
    }
    @Test void deveImpedirDenunciaDeConteudoDeOutraTurma() throws Exception {
        alunoDaTurma(); assertEquals("redirect:/turmas/30",controller.denunciarConteudo(30L,40L,"Motivo",session,flash));
        assertEquals("Este conteúdo não pertence à turma.",flash.getFlashAttributes().get("mensagemErro")); verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"semSessao","professor","emailAusente"})
    void deveBloquearDenunciaSemAlunoAutenticado(String caso) {
        if(caso.equals("semSessao")) session.clearAttributes(); else if(caso.equals("professor")) session.setAttribute("tipoUsuario","professor"); else session.removeAttribute("email2FA");
        assertEquals(caso.equals("professor")?"redirect:/turmas/30":"redirect:/login",controller.denunciarConteudo(30L,40L,"Motivo",session,flash)); verifyNoInteractions(service,alunos,turmas,conteudos);
    }
}
