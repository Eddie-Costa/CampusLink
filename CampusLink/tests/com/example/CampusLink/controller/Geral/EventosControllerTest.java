package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.*;
import com.example.CampusLink.dto.*;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventosControllerTest {
    @Mock EventoService eventos;
    @Mock DisponibilidadeService disponibilidades;
    @Mock ConteudoService conteudos;
    @Mock alunoDAO alunos;
    @Mock professorDAO professores;
    @Mock turmaDAO turmas;
    @Mock usuarioDAO usuarios;
    EventosController controller;
    MockHttpSession session;
    RedirectAttributesModelMap flash;
    final LocalDateTime inicio = LocalDateTime.of(2026,10,24,14,0);
    @BeforeEach void preparar() {
        controller = new EventosController(eventos, disponibilidades, conteudos, alunos, professores, turmas);
        ReflectionTestUtils.setField(controller,"usuarioDAO",usuarios);
        session = new MockHttpSession(); session.setAttribute("usuarioLogado",new Object());
        session.setAttribute("tipoUsuario","professor"); session.setAttribute("email2FA","professor@example.com");
        flash = new RedirectAttributesModelMap();
    }
    private void professorNaTurma() throws Exception {
        when(professores.buscarPorIDProfessor("professor@example.com")).thenReturn("20");
        var turma = new TurmaDTO(); turma.setId(30L); when(turmas.buscarTurmasDoUsuario("professor","20")).thenReturn(List.of(turma));
    }
    private String cadastrar(String nome, LocalDateTime fim, List<Long> ids) throws Exception {
        return controller.cadastrarEvento(nome,"PROVA","Descrição",inicio,fim,"ALTA",30L,ids,session,flash);
    }
    @Test void deveCadastrarEventoDaTurmaDoProfessor() throws Exception {
        professorNaTurma(); var conteudo = new ConteudoDTO(); conteudo.setId(40L); when(conteudos.listarPorTurma(30L)).thenReturn(List.of(conteudo));
        assertEquals("redirect:/eventos", cadastrar("Prova",inicio.plusHours(2),List.of(40L)));
        var captor = ArgumentCaptor.forClass(Evento.class); verify(eventos).adicionarEvento(captor.capture(),eq(List.of(40L)));
        var evento = captor.getValue(); assertEquals("Prova",evento.getNome()); assertEquals(20L,evento.getIdProfessor()); assertEquals(30L,evento.getIdTurma());
    }
    @ParameterizedTest @ValueSource(strings={"nome","datas","turma","conteudo"})
    void deveRecusarCadastroInvalido(String caso) throws Exception {
        when(professores.buscarPorIDProfessor("professor@example.com")).thenReturn("20");
        if(caso.equals("conteudo")) {
            var turma=new TurmaDTO(); turma.setId(30L); when(turmas.buscarTurmasDoUsuario("professor","20")).thenReturn(List.of(turma));
        }
        String retorno=cadastrar(caso.equals("nome")?" ":"Prova",caso.equals("datas")?inicio.minusMinutes(1):inicio.plusHours(1),List.of(999L));
        assertEquals("redirect:/eventos/cadastrar",retorno); assertNotNull(flash.getFlashAttributes().get("erro")); verifyNoInteractions(eventos);
    }
    @Test void deveAceitarInicioIgualAoFim() throws Exception {
        professorNaTurma(); assertEquals("redirect:/eventos",cadastrar("Prazo",inicio,List.of()));
        var captor=ArgumentCaptor.forClass(Evento.class); verify(eventos).adicionarEvento(captor.capture(),eq(List.of()));
        assertEquals(captor.getValue().getInicio(),captor.getValue().getFim());
    }
    @Test void deveImpedirExclusaoDeEventoDeOutroProfessor() throws Exception {
        when(professores.buscarPorIDProfessor("professor@example.com")).thenReturn("20");
        var evento=new Evento("Prova",inicio,inicio.plusHours(1),null); evento.setIdProfessor(99L); ReflectionTestUtils.setField(evento,"id",10L);
        when(eventos.listarEventos()).thenReturn(List.of(evento));
        assertEquals("redirect:/eventos",controller.excluirEvento(10L,session,flash));
        assertNotNull(flash.getFlashAttributes().get("erro")); verify(eventos,never()).excluirEvento(any());
    }
    @ParameterizedTest @ValueSource(strings={"semSessao","aluno"})
    void deveImpedirCadastroSemProfessor(String caso) throws Exception {
        if(caso.equals("semSessao")) session.clearAttributes(); else session.setAttribute("tipoUsuario","aluno");
        assertEquals(caso.equals("semSessao")?"redirect:/login":"redirect:/eventos",cadastrar("Prova",inicio.plusHours(1),List.of()));
        verifyNoInteractions(eventos,professores,turmas);
    }
    @ParameterizedTest @CsvSource({"0,true","24,true","-1,false","25,false"})
    void deveValidarLimitesDasHorasNoCalendarioDeEventos(int horas,boolean valido) throws Exception {
        session.setAttribute("tipoUsuario","aluno"); when(alunos.buscarPorIDAluno("professor@example.com")).thenReturn("10");
        var data=LocalDate.of(2026,10,24);
        assertEquals(valido?"redirect:/eventos":"redirect:/eventos/cadastrar",controller.cadastrarDisponibilidade(data,horas,session,flash));
        if(valido) verify(disponibilidades).salvarDisponibilidade(10L,data,horas);
        else { verifyNoInteractions(disponibilidades); assertNotNull(flash.getFlashAttributes().get("erro")); }
    }
}
