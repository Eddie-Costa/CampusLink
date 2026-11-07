package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArquivoController.class)
@Import(SecurityConfig.class)
class ArquivoAcessoTests {

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean ArquivoService arquivos;
    @MockitoBean ConteudoRepository conteudos;
    @MockitoBean AlunoRepository alunos;
    @MockitoBean ProfessorRepository professores;
    @MockitoBean SessaoLogListener sessoes;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean TermosService termos;

    @Test
    void alunoNaoBaixaArquivoDeTurmaDaQualNaoParticipa() throws Exception {
        when(arquivos.buscarPorId(7L)).thenReturn(arquivo(7L, 99L));
        when(alunos.buscarPorIDAluno("aluno@teste.com")).thenReturn("10");
        when(conteudos.usuarioTemAcessoAoConteudo(99L, "aluno", 10L)).thenReturn(false);

        mvc.perform(get("/arquivos/7/download").session(sessaoAluno()))
                .andExpect(status().isForbidden());
        verify(arquivos, never()).download(any());
    }

    @Test
    void alunoDaTurmaBaixaArquivo() throws Exception {
        when(arquivos.buscarPorId(7L)).thenReturn(arquivo(7L, 99L));
        when(alunos.buscarPorIDAluno("aluno@teste.com")).thenReturn("10");
        when(conteudos.usuarioTemAcessoAoConteudo(99L, "aluno", 10L)).thenReturn(true);
        when(conteudos.buscarStatus(99L)).thenReturn("ativo");
        when(arquivos.download(7L)).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/arquivos/7/download").session(sessaoAluno()))
                .andExpect(status().isOk());
    }

    @Test
    void arquivoInexistenteRetorna404() throws Exception {
        when(arquivos.buscarPorId(8L)).thenThrow(new IllegalArgumentException("Arquivo não encontrado."));

        mvc.perform(get("/arquivos/8/download").session(sessaoAluno()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sessaoSemLoginCompletoNaoExcluiNemEnviaArquivo() throws Exception {
        MockHttpSession cadastroIniciado = new MockHttpSession();
        cadastroIniciado.setAttribute("tipoUsuario", "professor");
        cadastroIniciado.setAttribute("email2FA", "qualquer@teste.com");

        mvc.perform(post("/arquivos/7/excluir").session(cadastroIniciado).with(csrf()));
        mvc.perform(multipart("/arquivos/upload").file(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[]{1}))
                .session(cadastroIniciado).with(csrf()));

        verify(arquivos, never()).excluir(any());
        verify(arquivos, never()).salvar(any(), any());
    }

    @Test
    void professorNaoExcluiArquivoDeConteudoAlheio() throws Exception {
        when(arquivos.buscarPorId(7L)).thenReturn(arquivo(7L, 99L));
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(conteudos.usuarioTemAcessoAoConteudo(99L, "professor", 20L)).thenReturn(false);

        mvc.perform(post("/arquivos/7/excluir").session(sessaoProfessor()).with(csrf()));

        verify(arquivos, never()).excluir(any());
    }

    @Test
    void professorNaoAnexaArquivoAConteudoAlheio() throws Exception {
        when(professores.buscarPorIDProfessor("prof@teste.com")).thenReturn("20");
        when(conteudos.usuarioTemAcessoAoConteudo(99L, "professor", 20L)).thenReturn(false);

        mvc.perform(multipart("/arquivos/upload").file(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[]{1}))
                .param("idConteudo", "99").session(sessaoProfessor()).with(csrf()));

        verify(arquivos, never()).salvar(any(), any());
    }

    private MockHttpSession sessaoAluno() {
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@teste.com");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", aluno);
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("email2FA", "aluno@teste.com");
        return session;
    }

    private MockHttpSession sessaoProfessor() {
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("prof@teste.com");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", professor);
        session.setAttribute("tipoUsuario", "professor");
        session.setAttribute("email2FA", "prof@teste.com");
        return session;
    }

    private ArquivoDTO arquivo(Long id, Long idConteudo) {
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setId(id);
        arquivo.setIdConteudo(idConteudo);
        arquivo.setNomeOriginal("material.pdf");
        arquivo.setMimeType("application/pdf");
        return arquivo;
    }
}
