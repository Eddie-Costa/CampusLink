package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DominioService;
import com.example.CampusLink.service.TermosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@WebMvcTest(turmasController.class)
@Import(SecurityConfig.class)
class TurmasAcessoTests {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean ProfessorRepository professores;
    @MockitoBean AlunoRepository alunos;
    @MockitoBean TurmaRepository turmas;
    @MockitoBean ConteudoService conteudos;
    @MockitoBean ArquivoService arquivos;
    @MockitoBean DominioService dominios;
    @MockitoBean SessaoLogListener sessoes;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean TermosService termos;

    @Test
    void alunoNaoAbreTurmaDaQualNaoParticipaTrocandoOIdNaUrl() throws Exception {
        MockHttpSession session = sessao("aluno", "aluno@teste.com");
        when(alunos.buscarPorIDAluno("aluno@teste.com")).thenReturn("10");
        when(turmas.buscarTurmasDoUsuario("aluno", "10")).thenReturn(List.of(turma(1L)));

        mvc.perform(get("/turmas/5").session(session))
                .andExpect(redirectedUrl("/turmas"))
                .andExpect(flash().attribute("mensagemErro", "Você não participa desta turma."));
        verify(conteudos, never()).buscarPorTurma(any(), any(), any());
    }

    @Test
    void idDeTurmaInvalidoVoltaParaListaDeTurmas() throws Exception {
        mvc.perform(get("/turmas/abc").session(sessao("aluno", "aluno@teste.com")))
                .andExpect(redirectedUrl("/turmas"));
    }

    @Test
    void professorDeOutraTurmaNaoExcluiConteudo() throws Exception {
        MockHttpSession session = sessao("professor", "prof@teste.com");
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(turmas.buscarTurmasDoUsuario("professor", "20")).thenReturn(List.of(turma(1L)));

        mvc.perform(post("/turmas/5/conteudos/99/excluir").session(session).with(csrf()))
                .andExpect(redirectedUrl("/turmas"));
        verify(conteudos, never()).excluir(any());
    }

    @Test
    void professorNaoExcluiConteudoDeOutraTurmaUsandoOIdDaSuaTurma() throws Exception {
        MockHttpSession session = sessao("professor", "prof@teste.com");
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(turmas.buscarTurmasDoUsuario("professor", "20")).thenReturn(List.of(turma(1L)));
        when(conteudos.pertenceATurma(99L, 1L)).thenReturn(false);

        mvc.perform(post("/turmas/1/conteudos/99/excluir").session(session).with(csrf()))
                .andExpect(redirectedUrl("/turmas/1"))
                .andExpect(flash().attribute("mensagemErro", "Este conteúdo não pertence a esta turma."));
        verify(conteudos, never()).excluir(any());
    }

    @Test
    void professorDaTurmaExcluiConteudoDela() throws Exception {
        MockHttpSession session = sessao("professor", "prof@teste.com");
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(turmas.buscarTurmasDoUsuario("professor", "20")).thenReturn(List.of(turma(1L)));
        when(conteudos.pertenceATurma(99L, 1L)).thenReturn(true);

        mvc.perform(post("/turmas/1/conteudos/99/excluir").session(session).with(csrf()))
                .andExpect(redirectedUrl("/turmas/1"));
        verify(conteudos).excluir(99L);
    }

    @Test
    void professorDeOutraTurmaNaoCriaConteudo() throws Exception {
        MockHttpSession session = sessao("professor", "prof@teste.com");
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(turmas.buscarTurmasDoUsuario("professor", "20")).thenReturn(List.of(turma(1L)));

        mvc.perform(post("/turmas/5/conteudos").session(session).with(csrf())
                        .param("titulo", "Invasão").param("descricao", "Teste"))
                .andExpect(redirectedUrl("/turmas"));
        verify(conteudos, never()).criar(any(), any());
    }

    @Test
    void sessaoDeCadastroIniciadoNaoCriaTurmaEmNomeDeOutroProfessor() throws Exception {
        MockHttpSession cadastroIniciado = new MockHttpSession();
        cadastroIniciado.setAttribute("tipoUsuario", "professor");
        cadastroIniciado.setAttribute("email2FA", "professor.real@teste.com");

        mvc.perform(post("/criarTurma").session(cadastroIniciado).with(csrf())
                        .param("nomeTurma", "Turma falsa"))
                .andExpect(redirectedUrl("/login"));
        verify(professores, never()).InsertTurmasIntoBD(any(), any(), any());
    }

    private MockHttpSession sessao(String tipo, String email) {
        Object usuario;
        if ("aluno".equals(tipo)) {
            loginAlunoDTO aluno = new loginAlunoDTO();
            aluno.setEmail(email);
            usuario = aluno;
        } else {
            loginProfessorDTO professor = new loginProfessorDTO();
            professor.setEmail(email);
            usuario = professor;
        }

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", usuario);
        session.setAttribute("tipoUsuario", tipo);
        session.setAttribute("email2FA", email);
        return session;
    }

    private TurmaDTO turma(Long id) {
        TurmaDTO turma = new TurmaDTO();
        turma.setId(id);
        return turma;
    }
}
