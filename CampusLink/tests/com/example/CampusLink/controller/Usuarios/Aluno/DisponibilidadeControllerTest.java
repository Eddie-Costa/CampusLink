package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeControllerTest {
    @Mock alunoDAO alunos;
    @Mock EventoService eventos;
    @Mock DisponibilidadeService disponibilidades;
    @Mock usuarioDAO usuarios;
    DisponibilidadeController controller;
    MockHttpSession session;
    RedirectAttributesModelMap flash;
    final LocalDate data=LocalDate.of(2026,10,24);
    @BeforeEach void preparar() {
        controller=new DisponibilidadeController(alunos,eventos,disponibilidades); ReflectionTestUtils.setField(controller,"usuarioDAO",usuarios);
        session=new MockHttpSession(); session.setAttribute("usuarioLogado",new Object()); session.setAttribute("tipoUsuario","aluno"); session.setAttribute("email2FA","aluno@example.com");
        flash=new RedirectAttributesModelMap();
    }
    @ParameterizedTest @CsvSource({"0,true","24,true","-1,false","25,false"})
    void deveValidarLimitesDasHoras(int horas,boolean valido) throws Exception {
        when(alunos.buscarPorIDAluno("aluno@example.com")).thenReturn("10");
        assertEquals(valido?"redirect:/disponibilidade":"redirect:/disponibilidade/cadastrar",controller.cadastrarDisponibilidade(data,horas,session,flash));
        if(valido) verify(disponibilidades).salvarDisponibilidade(10L,data,horas);
        else {verifyNoInteractions(disponibilidades); assertNotNull(flash.getFlashAttributes().get("erro"));}
    }
    @Test void deveRecusarHorasNulas() throws Exception {
        when(alunos.buscarPorIDAluno("aluno@example.com")).thenReturn("10");
        assertEquals("redirect:/disponibilidade/cadastrar",controller.cadastrarDisponibilidade(data,null,session,flash)); verifyNoInteractions(disponibilidades);
    }
    @ParameterizedTest @ValueSource(strings={"semSessao","professor","emailAusente","alunoInexistente"})
    void deveRecusarCadastroSemAlunoIdentificado(String caso) throws Exception {
        if(caso.equals("semSessao")) session.clearAttributes();
        else if(caso.equals("professor")) session.setAttribute("tipoUsuario","professor");
        else if(caso.equals("emailAusente")) session.removeAttribute("email2FA");
        assertEquals("redirect:/login",controller.cadastrarDisponibilidade(data,4,session,flash)); verifyNoInteractions(disponibilidades);
    }
}
