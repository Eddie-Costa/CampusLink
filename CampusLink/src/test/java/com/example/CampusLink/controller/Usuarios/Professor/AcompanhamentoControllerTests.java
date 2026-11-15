// verificacao do acompanhamento do professor confere acesso pela turma contagem de riscos alunos atrasados e situacao por evento
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.AdminTurmaRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.EventoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ViabilidadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// testes do acompanhamento esta classe simula a turma 1 do professor 4 com os alunos 7 e 8 e um evento futuro e outro passado
class AcompanhamentoControllerTests {

    private static final String PROFESSOR = "prof@teste.com";
    private static final LocalDateTime AGORA = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

    private EventoRepository eventos;
    private ConteudoRepository conteudos;
    private ViabilidadeService viabilidade;
    private AcompanhamentoController controlador;
    private ExtendedModelMap modelo;
    private RedirectAttributesModelMap redirecionamento;

    // preparar testes do acompanhamento este metodo cria a turma os dois alunos e os eventos 5 futuro e 6 passado
    @BeforeEach
    void preparar() throws SQLException {
        ProfessorRepository professores = mock(ProfessorRepository.class);
        TurmaRepository turmas = mock(TurmaRepository.class);
        AdminTurmaRepository participantes = mock(AdminTurmaRepository.class);
        eventos = mock(EventoRepository.class);
        conteudos = mock(ConteudoRepository.class);
        viabilidade = mock(ViabilidadeService.class);
        controlador = new AcompanhamentoController(professores, turmas, participantes, eventos, conteudos, viabilidade);
        ReflectionTestUtils.setField(controlador, "UsuarioRepository", mock(UsuarioRepository.class));
        modelo = new ExtendedModelMap();
        redirecionamento = new RedirectAttributesModelMap();
        TurmaDTO turma = new TurmaDTO();
        turma.setId(1L);
        turma.setNomeTurma("Calculo");
        when(professores.buscarPorIDProfessor(PROFESSOR)).thenReturn("4");
        when(professores.buscarPorIDProfessor("sem@teste.com")).thenReturn(null);
        when(turmas.buscarTurmasDoUsuario("professor", "4")).thenReturn(List.of(turma));
        when(participantes.listarAlunosDaTurma(1L)).thenReturn(List.of(aluno(7L, "Bruno"), aluno(8L, "ana")));
        Evento futuro = evento(5L, AGORA.plusDays(3));
        Evento passado = evento(6L, AGORA.minusDays(3));
        when(eventos.findByIdTurmaInAndStatusAndInicioGreaterThanEqualOrderByInicioAsc(anyList(), anyString(), any())).thenReturn(List.of(futuro));
        when(eventos.findByIdTurmaInAndStatusAndInicioLessThanOrderByInicioDesc(anyList(), anyString(), any())).thenReturn(List.of(passado));
        when(eventos.findById(5L)).thenReturn(Optional.of(futuro));
        when(eventos.findById(6L)).thenReturn(Optional.of(passado));
        when(conteudos.listarPorEventos(anyList())).thenReturn(new java.util.HashMap<>(Map.of(5L, List.of(material(10L), material(11L)))));
        when(conteudos.listarPorEvento(5L)).thenReturn(List.of(material(10L), material(11L)));
        when(conteudos.listarPorEvento(6L)).thenReturn(List.of(material(12L), material(13L)));
        Map<Long, List<ViabilidadeDTO>> resultados = Map.of(
                7L, List.of(item(5L, null, "vermelho"), item(5L, 10L, "vermelho"), item(5L, 11L, "concluido"), item(99L, null, "verde")),
                8L, List.of(item(5L, null, "verde"), item(5L, 10L, "vermelho"), item(5L, 11L, "verde")));
        when(viabilidade.calcularParaAlunos(anyList())).thenReturn(resultados);
        // telas calculam so ate o ultimo evento que mostram, por isso usam o calculo com data limite
        when(viabilidade.calcularParaAlunosAte(anyList(), any())).thenReturn(resultados);
        when(viabilidade.listarAtrasados(anyList())).thenReturn(Map.of(7L, List.of(item(6L, 12L, "atrasado"))));
        when(viabilidade.minutosConcluidosRecentes(anyList(), anyInt())).thenReturn(Map.of(7L, 90));
        when(viabilidade.formatarHoras(anyInt())).thenAnswer(chamada -> chamada.getArgument(0) + "min");
    }

    // testar lista de turmas e acesso este teste confere a lista do professor e as recusas de visitante aluno e professor sem cadastro
    @Test
    void listaTurmasSoParaProfessorCadastrado() throws SQLException {
        // preparar sessoes

        // executar telas e verificar destinos
        assertEquals("Usuarios/Professor/acompanhamento", controlador.exibirAcompanhamento(modelo, sessao(PROFESSOR, "professor")));
        assertEquals("redirect:/login", controlador.exibirAcompanhamento(modelo, new MockHttpSession()));
        assertEquals("redirect:/turmas", controlador.exibirAcompanhamento(modelo, sessao("aluno@teste.com", "aluno")));
        assertEquals("redirect:/login", controlador.exibirAcompanhamento(modelo, sessao("sem@teste.com", "professor")));
        assertEquals("redirect:/login", controlador.exibirAcompanhamentoTurma(1L, modelo, sessao("sem@teste.com", "professor"), redirecionamento));
        assertEquals("redirect:/turmas", controlador.exibirAcompanhamentoEvento(1L, 5L, modelo, sessao("aluno@teste.com", "aluno"), redirecionamento));
    }

    // testar painel da turma este teste confere contagem por evento e material ranking critico e alunos atrasados
    @Test
    @SuppressWarnings("unchecked")
    void montaPainelDaTurmaComRiscos() throws SQLException {
        // preparar resultados de dois alunos

        // executar painel da turma
        String tela = controlador.exibirAcompanhamentoTurma(1L, modelo, sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar contagens ranking e atrasos
        assertEquals("Usuarios/Professor/acompanhamentoTurma", tela);
        Map<Long, Map<String, Long>> porEvento = (Map<Long, Map<String, Long>>) modelo.get("contagemPorEvento");
        assertEquals(1L, porEvento.get(5L).get("vermelho"));
        assertEquals(1L, porEvento.get(5L).get("verde"));
        Map<Long, Map<Long, Map<String, Long>>> porMaterial = (Map<Long, Map<Long, Map<String, Long>>>) modelo.get("contagemPorConteudo");
        assertEquals(2L, porMaterial.get(5L).get(10L).get("vermelho"));
        List<Map<String, Object>> criticos = (List<Map<String, Object>>) modelo.get("conteudosCriticos");
        assertEquals(2L, criticos.get(0).get("vermelhos"));
        List<Map<String, Object>> atrasados = (List<Map<String, Object>>) modelo.get("alunosComAtraso");
        assertEquals(List.of(Map.of("nome", "Bruno", "atrasados", 1L)), atrasados);
        assertEquals(2, modelo.get("totalAlunos"));
    }

    // testar turma ou evento fora do acesso este teste confere a recusa de turma alheia e de evento de outra turma
    @Test
    void recusaTurmaAlheiaEEventoDeOutraTurma() throws SQLException {
        // preparar evento 9 de outra turma
        Evento outro = evento(9L, AGORA.plusDays(1));
        outro.setIdTurma(2L);
        when(eventos.findById(9L)).thenReturn(Optional.of(outro));

        // executar telas e verificar destinos e mensagens
        assertEquals("redirect:/acompanhamento", controlador.exibirAcompanhamentoTurma(2L, modelo, sessao(PROFESSOR, "professor"), redirecionamento));
        assertEquals("Você não tem acesso a esta turma.", redirecionamento.getFlashAttributes().get("mensagemErro"));
        assertEquals("redirect:/acompanhamento", controlador.exibirAcompanhamentoEvento(2L, 5L, modelo, sessao(PROFESSOR, "professor"), redirecionamento));
        assertEquals("redirect:/acompanhamento/turma/1", controlador.exibirAcompanhamentoEvento(1L, 9L, modelo, sessao(PROFESSOR, "professor"), redirecionamento));
        assertEquals("redirect:/acompanhamento/turma/1", controlador.exibirAcompanhamentoEvento(1L, 77L, modelo, sessao(PROFESSOR, "professor"), redirecionamento));
        assertEquals("Evento não encontrado nesta turma.", redirecionamento.getFlashAttributes().get("mensagemErro"));
    }

    // testar evento futuro este teste confere a situacao de cada aluno pela viabilidade e a ordem pela gravidade
    @Test
    @SuppressWarnings("unchecked")
    void mostraEventoFuturoOrdenadoPelaGravidade() throws SQLException {
        // preparar evento 5 futuro

        // executar tela do evento
        String tela = controlador.exibirAcompanhamentoEvento(1L, 5L, modelo, sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar situacoes concluidos ordem e horas da semana
        assertEquals("Usuarios/Professor/acompanhamentoEvento", tela);
        assertEquals(Map.of(7L, "vermelho", 8L, "verde"), modelo.get("situacaoPorAluno"));
        assertEquals(Map.of(7L, 1L, 8L, 0L), modelo.get("concluidosPorAluno"));
        assertEquals(List.of(7L, 8L), ((List<usuarioAdminDTO>) modelo.get("alunos")).stream().map(usuarioAdminDTO::getIdPerfil).toList());
        assertEquals(Map.of(7L, "90min", 8L, "0min"), modelo.get("horasSemanaPorAluno"));
        assertEquals(false, modelo.get("eventoPassado"));
    }

    // testar evento passado este teste confere atrasado para quem nao concluiu tudo e concluido para quem terminou
    @Test
    @SuppressWarnings("unchecked")
    void mostraEventoPassadoComConcluidosEAtrasados() throws SQLException {
        // preparar aluno 8 com tudo feito e aluno 7 com metade
        when(conteudos.listarConcluidosDosAlunos(anyList())).thenReturn(Map.of(7L, Set.of(12L), 8L, Set.of(12L, 13L)));

        // executar tela do evento passado
        controlador.exibirAcompanhamentoEvento(1L, 6L, modelo, sessao(PROFESSOR, "professor"), redirecionamento);

        // verificar situacoes e niveis por material
        assertEquals(true, modelo.get("eventoPassado"));
        assertEquals(Map.of(7L, "atrasado", 8L, "concluido"), modelo.get("situacaoPorAluno"));
        Map<Long, Map<Long, String>> niveis = (Map<Long, Map<Long, String>>) modelo.get("nivelPorAlunoEConteudo");
        assertEquals(Map.of(12L, "concluido", 13L, "atrasado"), niveis.get(7L));
        assertEquals(List.of(7L, 8L), ((List<usuarioAdminDTO>) modelo.get("alunos")).stream().map(usuarioAdminDTO::getIdPerfil).toList());
    }

    // montar sessao este metodo cria a sessao logada com email e perfil
    private MockHttpSession sessao(String email, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", new Object());
        sessao.setAttribute("email2FA", email);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }

    // montar aluno este metodo cria o aluno da turma com codigo e nome
    private usuarioAdminDTO aluno(Long id, String nome) {
        usuarioAdminDTO aluno = new usuarioAdminDTO();
        aluno.setIdPerfil(id);
        aluno.setNome(nome);
        return aluno;
    }

    // montar evento este metodo cria o evento da turma 1 na data informada
    private Evento evento(Long id, LocalDateTime inicio) {
        Evento evento = new Evento("Prova " + id, inicio, inicio.plusHours(2), null);
        ReflectionTestUtils.setField(evento, "id", id);
        evento.setIdTurma(1L);
        return evento;
    }

    // montar material este metodo cria o material com codigo
    private ConteudoDTO material(Long id) {
        ConteudoDTO conteudo = new ConteudoDTO();
        conteudo.setId(id);
        return conteudo;
    }

    // montar resultado este metodo cria o resultado da turma 1 para o evento e material com o nivel informado
    private ViabilidadeDTO item(Long idEvento, Long idConteudo, String nivel) {
        ViabilidadeDTO item = new ViabilidadeDTO();
        item.setIdTurma(1L);
        item.setIdEvento(idEvento);
        item.setIdConteudo(idConteudo);
        item.setTituloConteudo(idConteudo == null ? null : "Material " + idConteudo);
        item.setNivel(nivel);
        return item;
    }
}
