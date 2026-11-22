// verificacao da tela do calendario do aluno desenha a pagina com situacao dos eventos e prioridades por evento
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.dto.ViabilidadeDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.DashboardRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import com.example.CampusLink.service.TermosService;
import com.example.CampusLink.service.ViabilidadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// testes da tela do calendario esta classe simula uma aluna com um evento em replanejar no mes atual
@WebMvcTest(EventosController.class)
@Import(SecurityConfig.class)
class CalendarioAlunoTelaTests {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean EventoService eventos;
    @MockitoBean DisponibilidadeService disponibilidades;
    @MockitoBean ConteudoService conteudos;
    @MockitoBean AlunoRepository alunos;
    @MockitoBean ProfessorRepository professores;
    @MockitoBean TurmaRepository turmas;
    // o template chama @viabilidadeService para formatar horas, entao o mock precisa do nome do bean real
    @MockitoBean(name = "viabilidadeService") ViabilidadeService viabilidade;
    @MockitoBean ConteudoRepository conteudoRepository;
    @MockitoBean DashboardRepository dashboard;
    @MockitoBean SessaoLogListener sessoes;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean TermosService termos;

    // testar tela do aluno este teste confere a situacao no cartao do evento e a janela da prioridade com os conteudos
    @Test
    void mostraSituacaoDoEventoComHorasNecessariasELivres() throws Exception {
        // preparar aluna, turma, evento daqui a dois dias e o resumo de risco do evento
        LocalDateTime inicio = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).plusDays(2).withHour(19).withMinute(0);
        Evento prova = new Evento("Prova de Estatística", inicio, inicio.plusHours(2), null, "ALTA");
        ReflectionTestUtils.setField(prova, "id", 5L);
        prova.setIdTurma(1L);
        prova.setStatus("ATIVO");
        TurmaDTO turma = new TurmaDTO();
        turma.setId(1L);
        turma.setNomeTurma("Estatística Aplicada");

        ViabilidadeDTO resumo = risco(null, "Faltam 10h de estudo e você tem 6h disponíveis.");
        resumo.setCargaMin(600);
        resumo.setCargaEventoMin(300);
        resumo.setCapacidadeMin(360);
        ViabilidadeDTO pendente = risco(10L, "Faltam 4h.");
        pendente.setTituloConteudo("Testes de hipótese");

        when(alunos.buscarPorIDAluno("aluna@teste.com")).thenReturn("7");
        when(turmas.buscarTurmasDoUsuario("aluno", "7")).thenReturn(List.of(turma));
        when(eventos.listarEventosDasTurmasNoPeriodo(any(), any(), any())).thenReturn(List.of(prova));
        when(eventos.listarEventosDasTurmas(any())).thenReturn(List.of(prova));
        when(conteudoRepository.listarPorEventos(any())).thenReturn(Map.of());
        when(conteudoRepository.listarConcluidosDosAlunos(any())).thenReturn(Map.of());
        when(viabilidade.calcularParaAluno(7L)).thenReturn(List.of(resumo, pendente));
        when(viabilidade.listarEventosPrioritarios(any())).thenReturn(List.of(resumo));
        when(viabilidade.conteudosPendentesPorEvento(any())).thenReturn(Map.of(5L, List.of(pendente)));
        when(viabilidade.resumosPorEvento(any())).thenReturn(Map.of(5L, resumo));
        when(viabilidade.proximoEventoPendente(any())).thenReturn(resumo);
        when(viabilidade.formatarHoras(anyInt())).thenAnswer(chamada -> ((Integer) chamada.getArgument(0)) / 60 + "h");

        // executar a tela do calendario
        String html = mvc.perform(get("/eventos").session(sessaoDaAluna()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estudo deste evento")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // verificar janela da prioridade, horas do evento e conteudo pendente
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("id=\"prioridadeEvento5\""), "janela da prioridade");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Testes de hipótese"), "conteudo pendente");
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(">10h<") && html.contains(">6h<") && html.contains(">4h<"),
                "horas necessarias, livres e que faltam");
    }

    // montar risco do evento este metodo cria a linha de viabilidade em replanejar do evento 5
    private ViabilidadeDTO risco(Long idConteudo, String motivo) {
        ViabilidadeDTO risco = new ViabilidadeDTO();
        risco.setIdEvento(5L);
        risco.setIdTurma(1L);
        risco.setIdConteudo(idConteudo);
        risco.setNomeEvento("Prova de Estatística");
        risco.setDataEvento(LocalDateTime.now().plusDays(2));
        risco.setNivel("amarelo");
        risco.setMotivo(motivo);
        return risco;
    }

    // montar sessao da aluna este metodo simula o login completo com verificacao em duas etapas
    private MockHttpSession sessaoDaAluna() {
        loginAlunoDTO aluna = new loginAlunoDTO();
        aluna.setEmail("aluna@teste.com");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", aluna);
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("email2FA", "aluna@teste.com");
        return session;
    }
}
