// verificacao da agenda confere eventos por turma cadastro validado exclusao pelo autor e horas livres do aluno
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import com.example.CampusLink.service.ViabilidadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes da agenda esta classe simula o professor 4 e o aluno 7 na turma 1 e um evento de outra turma
class EventosControllerTests {

    private static final String PROFESSOR = "prof@teste.com";
    private static final String ALUNO = "aluno@teste.com";
    private static final LocalDateTime INICIO = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).plusDays(3).withHour(19).withMinute(0);

    private EventoService eventos;
    private DisponibilidadeService horas;
    private ConteudoService conteudos;
    private ViabilidadeService viabilidade;
    private EventosController controlador;
    private ExtendedModelMap modelo;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes da agenda este metodo cria o controlador com servicos simulados e os eventos 5 e 6
    @BeforeEach
    void preparar() throws SQLException {
        eventos = mock(EventoService.class);
        horas = mock(DisponibilidadeService.class);
        conteudos = mock(ConteudoService.class);
        AlunoRepository alunos = mock(AlunoRepository.class);
        ProfessorRepository professores = mock(ProfessorRepository.class);
        TurmaRepository turmas = mock(TurmaRepository.class);
        viabilidade = mock(ViabilidadeService.class);
        ConteudoRepository materiais = mock(ConteudoRepository.class);
        controlador = new EventosController(eventos, horas, conteudos, alunos, professores, turmas, viabilidade, materiais);
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        modelo = new ExtendedModelMap();
        redirecionamento = new RedirectAttributesModelMap();
        TurmaDTO turma = new TurmaDTO();
        turma.setId(1L);
        turma.setNomeTurma("Calculo");
        when(professores.buscarPorIDProfessor(PROFESSOR)).thenReturn("4");
        when(professores.buscarPorIDProfessor("sem@teste.com")).thenReturn("");
        when(alunos.buscarPorIDAluno(ALUNO)).thenReturn("7");
        when(turmas.buscarTurmasDoUsuario("professor", "4")).thenReturn(List.of(turma));
        when(turmas.buscarTurmasDoUsuario("aluno", "7")).thenReturn(List.of(turma));
        Evento eventoDaTurma = evento(5L, 1L, 4L);
        Evento eventoDeOutraTurma = evento(6L, 2L, 9L);
        when(eventos.listarEventos()).thenReturn(List.of(eventoDaTurma, eventoDeOutraTurma));
        // agenda busca so as turmas do usuario no mes visivel e a exclusao busca o evento pelo id
        when(eventos.listarEventosDasTurmasNoPeriodo(any(), any(), any())).thenAnswer(chamada -> List.of(eventoDaTurma, eventoDeOutraTurma).stream()
                .filter(evento -> ((java.util.Collection<?>) chamada.getArgument(0)).contains(evento.getIdTurma())).toList());
        when(eventos.listarEventosDasTurmas(any())).thenAnswer(chamada -> List.of(eventoDaTurma, eventoDeOutraTurma).stream()
                .filter(evento -> ((java.util.Collection<?>) chamada.getArgument(0)).contains(evento.getIdTurma())).toList());
        when(eventos.buscarEventoPorId(5L)).thenReturn(eventoDaTurma);
        when(eventos.buscarEventoPorId(6L)).thenReturn(eventoDeOutraTurma);
        when(conteudos.listarPorTurma(1L)).thenReturn(List.of(material(10L), material(11L)));
        when(materiais.listarConcluidosDosAlunos(List.of(7L))).thenReturn(Map.of(7L, Set.of(10L)));
    }

    // testar agenda do professor este teste confere que o professor ve apenas eventos das suas turmas
    @Test
    @SuppressWarnings("unchecked")
    void mostraAgendaDoProfessorSoComSuasTurmas() throws SQLException {
        // preparar sessao do professor

        // executar agenda e formulario
        String agenda = controlador.exibirCalendario(modelo, sessao(PROFESSOR, "professor"), null);
        String formulario = controlador.exibirFormularioCadastro(new ExtendedModelMap(), sessao(PROFESSOR, "professor"));
        String semLogin = controlador.exibirCalendario(modelo, new MockHttpSession(), null);

        // verificar telas e evento de outra turma escondido
        assertEquals("Geral/calendario-eventos", agenda);
        assertEquals("Geral/cadastrar-eventos", formulario);
        assertEquals("redirect:/login", semLogin);
        assertEquals(List.of(5L), ((List<Evento>) modelo.get("eventos")).stream().map(Evento::getId).toList());
    }

    // testar evento cancelado este teste confere que o cancelado da propria turma nao aparece no calendario
    @Test
    @SuppressWarnings("unchecked")
    void escondeEventoCanceladoDoCalendario() throws SQLException {
        // preparar evento cancelado na turma do aluno junto do evento ativo
        Evento cancelado = evento(8L, 1L, 4L);
        cancelado.setStatus("CANCELADO");
        org.mockito.Mockito.doReturn(List.of(evento(5L, 1L, 4L), cancelado))
                .when(eventos).listarEventosDasTurmasNoPeriodo(any(), any(), any());

        // executar agenda do aluno
        controlador.exibirCalendario(modelo, sessao(ALUNO, "aluno"), null);

        // verificar so o evento ativo na tela
        assertEquals(List.of(5L), ((List<Evento>) modelo.get("eventos")).stream().map(Evento::getId).toList());
    }

    // testar agenda do aluno este teste confere eventos materiais concluidos viabilidade e plano de estudos
    @Test
    @SuppressWarnings("unchecked")
    void mostraAgendaDoAlunoComPlanoDeEstudos() throws SQLException {
        // preparar sessao do aluno

        // executar agenda
        controlador.exibirCalendario(modelo, sessao(ALUNO, "aluno"), null);

        // verificar dados da agenda do aluno
        assertEquals(List.of(5L), ((List<Evento>) modelo.get("eventos")).stream().map(Evento::getId).toList());
        assertEquals(Set.of(10L), modelo.get("conteudosConcluidos"));
        assertEquals(Map.of(1L, "Calculo"), modelo.get("nomeTurmaPorId"));
        verify(viabilidade).calcularParaAluno(7L);
        verify(viabilidade).gerarPlano(7L);
        verify(horas).listarPorAluno(7L);
    }

    // testar materiais para o evento este teste confere a lista com prazos para o professor da turma e vazia para os demais
    @Test
    void listaMateriaisDaTurmaSoParaProfessorDaTurma() throws SQLException {
        // preparar prazo encerrado do material 10
        LocalDateTime prazo = LocalDateTime.of(2026, 11, 1, 19, 0);
        when(conteudos.listarPrazosEncerrados(List.of(10L, 11L))).thenReturn(Map.of(10L, prazo));

        // executar consultas
        List<ConteudoDTO> daTurma = controlador.listarConteudosDaTurma(1L, sessao(PROFESSOR, "professor"));
        List<ConteudoDTO> outraTurma = controlador.listarConteudosDaTurma(2L, sessao(PROFESSOR, "professor"));
        List<ConteudoDTO> aluno = controlador.listarConteudosDaTurma(1L, sessao(ALUNO, "aluno"));
        List<ConteudoDTO> semCadastro = controlador.listarConteudosDaTurma(1L, sessao("sem@teste.com", "professor"));

        // verificar listas e prazo
        assertEquals(prazo, daTurma.get(0).getPrazoEncerradoEm());
        assertTrue(outraTurma.isEmpty());
        assertTrue(aluno.isEmpty());
        assertTrue(semCadastro.isEmpty());
    }

    // testar cadastro do evento este teste confere o evento salvo com materiais da turma e a atividade registrada
    @Test
    void cadastraEventoComMateriaisDaTurma() throws SQLException {
        // preparar dados validos

        // executar cadastro
        String destino = controlador.cadastrarEvento("Prova", "prova", "Capitulo 1", INICIO, INICIO.plusHours(2), "ALTA", 1L,
                List.of(10L), sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar evento salvo e mensagem
        assertEquals("redirect:/eventos", destino);
        assertEquals("Evento cadastrado com sucesso!", redirecionamento.getFlashAttributes().get("mensagem"));
        verify(eventos).adicionarEvento(org.mockito.ArgumentMatchers.argThat(e -> e.getIdTurma() == 1L && e.getIdProfessor() == 4L
                && "ALTA".equals(e.getPrioridade())), org.mockito.ArgumentMatchers.eq(List.of(10L)));
    }

    // testar cadastro invalido este teste confere cada recusa do cadastro sem salvar o evento
    @Test
    void recusaCadastroInvalido() throws SQLException {
        // preparar dados invalidos

        // executar cadastros e verificar mensagens
        assertEquals("Informe o nome do evento.", erroCadastro(" ", INICIO, INICIO.plusHours(1), 1L, null, PROFESSOR));
        assertEquals("O horário de término deve ser depois do início.", erroCadastro("Prova", INICIO, INICIO, 1L, null, PROFESSOR));
        assertEquals("Voce nao possui acesso a turma selecionada", erroCadastro("Prova", INICIO, INICIO.plusHours(1), 2L, null, PROFESSOR));
        assertEquals("Um dos conteudos nao pertence a turma", erroCadastro("Prova", INICIO, INICIO.plusHours(1), 1L, List.of(10L, 99L), PROFESSOR));
        assertEquals("Nao foi possivel identificar o professor", erroCadastro("Prova", INICIO, INICIO.plusHours(1), 1L, null, "sem@teste.com"));
        assertEquals("redirect:/eventos", controlador.cadastrarEvento("Prova", null, null, INICIO, INICIO.plusHours(1), "MEDIA", 1L, null,
                sessao(ALUNO, "aluno"), redirecionamento));
        assertEquals("redirect:/login", controlador.cadastrarEvento("Prova", null, null, INICIO, INICIO.plusHours(1), "MEDIA", 1L, null,
                new MockHttpSession(), redirecionamento));
        verify(eventos, never()).adicionarEvento(any(), any());
    }

    // testar exclusao do evento este teste confere que so o autor exclui e as recusas de evento inexistente ou alheio
    @Test
    void excluiApenasEventoDoProprioProfessor() throws SQLException {
        // preparar evento 5 do professor 4 e evento 6 de outro professor

        // executar exclusoes e verificar mensagens
        assertEquals("Evento excluido com sucesso!", exclusao(5L, PROFESSOR, "mensagem"));
        assertEquals("Voce nao possui permissao para excluir este evento", exclusao(6L, PROFESSOR, "erro"));
        assertEquals("Evento nao encontrado", exclusao(99L, PROFESSOR, "erro"));
        assertEquals("Nao foi possivel identificar o professor", exclusao(5L, "sem@teste.com", "erro"));
        assertEquals("redirect:/eventos", controlador.excluirEvento(5L, sessao(ALUNO, "aluno"), redirecionamento));
        assertEquals("redirect:/login", controlador.excluirEvento(5L, new MockHttpSession(), redirecionamento));
        verify(eventos).excluirEvento(5L);
        verify(eventos, never()).excluirEvento(6L);
    }

    // testar horas livres este teste confere o registro do aluno e as recusas de quantidade dia passado e perfil
    @Test
    void registraHorasLivresDoAluno() throws SQLException {
        // preparar amanha e ontem
        LocalDate amanha = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(1);

        // executar registros e verificar mensagens
        assertEquals("Disponibilidade cadastrada com sucesso!", horasLivres(amanha, 3, ALUNO, "mensagem"));
        assertEquals("Informe uma quantidade entre 0 e 24 horas", horasLivres(amanha, 25, ALUNO, "erro"));
        assertEquals("Informe uma quantidade entre 0 e 24 horas", horasLivres(amanha, -1, ALUNO, "erro"));
        assertTrue(horasLivres(amanha.minusDays(2), 2, ALUNO, "erro").contains("já passaram"));
        assertEquals("Nao foi possivel identificar o aluno", horasLivres(amanha, 2, "sem@teste.com", "erro"));
        assertEquals("redirect:/eventos", controlador.cadastrarDisponibilidade(amanha, 2, sessao(PROFESSOR, "professor"), redirecionamento));
        assertEquals("redirect:/login", controlador.cadastrarDisponibilidade(amanha, 2, new MockHttpSession(), redirecionamento));
        verify(horas).salvarDisponibilidade(7L, amanha, 3);
        verify(horas, never()).salvarDisponibilidade(anyLong(), any(), org.mockito.ArgumentMatchers.eq(25));
    }

    // tentar cadastro este metodo envia o cadastro do evento e devolve a mensagem de erro
    private String erroCadastro(String nome, LocalDateTime inicio, LocalDateTime fim, Long turma, List<Long> materiais, String email) throws SQLException {
        RedirectAttributesModelMap mensagens = new RedirectAttributesModelMap();
        String destino = controlador.cadastrarEvento(nome, null, null, inicio, fim, "MEDIA", turma, materiais, sessao(email, "professor"), mensagens);
        assertEquals("redirect:/eventos/cadastrar", destino);
        return (String) mensagens.getFlashAttributes().get("erro");
    }

    // tentar exclusao este metodo pede a exclusao e devolve a mensagem pedida
    private String exclusao(Long id, String email, String chave) throws SQLException {
        RedirectAttributesModelMap mensagens = new RedirectAttributesModelMap();
        assertEquals("redirect:/eventos", controlador.excluirEvento(id, sessao(email, "professor"), mensagens));
        return (String) mensagens.getFlashAttributes().get(chave);
    }

    // tentar registro de horas este metodo envia as horas livres e devolve a mensagem pedida
    private String horasLivres(LocalDate dia, Integer quantidade, String email, String chave) throws SQLException {
        RedirectAttributesModelMap mensagens = new RedirectAttributesModelMap();
        controlador.cadastrarDisponibilidade(dia, quantidade, sessao(email, "aluno"), mensagens);
        return (String) mensagens.getFlashAttributes().get(chave);
    }

    // montar sessao este metodo cria a sessao logada com email e perfil
    private MockHttpSession sessao(String email, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", new Object());
        sessao.setAttribute("email2FA", email);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }

    // montar evento este metodo cria o evento com codigo turma e professor
    private Evento evento(Long id, Long idTurma, Long idProfessor) {
        Evento evento = new Evento("Evento " + id, INICIO, INICIO.plusHours(2), null);
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setIdTurma(idTurma);
        evento.setIdProfessor(idProfessor);
        return evento;
    }

    // montar material este metodo cria o material com codigo
    private ConteudoDTO material(Long id) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        return conteudo;
    }
}
