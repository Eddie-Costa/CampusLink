package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, TwoFactorService.class})
class ProfileControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean usuarioDAO usuarios;
    @MockitoBean emailService emails;
    @MockitoBean SessaoLogListener sessoes;
    private MockHttpSession session;
    private DadosUsuarioDTO dados;

    @BeforeEach
    void preparar() throws Exception {
        session = new MockHttpSession();
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("titular@example.com");
        aluno.setSenha("hash-que-nao-deve-ser-exposto");
        session.setAttribute("usuarioLogado", aluno);
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("email2FA", "outro@example.com");
        dados = new DadosUsuarioDTO();
        dados.setId(42L);
        dados.setNome("Ana Campus");
        dados.setEmail("titular@example.com");
        dados.setTelefone("11999990000");
        dados.setDataNascimento(LocalDate.of(2001, 2, 3));
        dados.setPerfil("ALUNOS");
        dados.setIdentificador("12345678901");
        dados.setDataCadastro(LocalDate.of(2026, 9, 1));
        when(usuarios.consultarDadosPessoais("titular@example.com")).thenReturn(dados);
    }

    @Test
    void renderizaPerfilComCsrfESomenteDadosDoTitular() throws Exception {
        String html = mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Ana Campus")))
                .andExpect(content().string(containsString("03/02/2001")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("hash-que-nao-deve-ser-exposto"))))
                .andExpect(content().string(not(containsString("outro@example.com"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        preview("aluno.html", html);
        verify(usuarios).consultarDadosPessoais("titular@example.com");
    }

    @Test
    void renderizaProfessorComVinculosSemBotaoDeExclusao() throws Exception {
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("titular@example.com");
        session.setAttribute("usuarioLogado", professor);
        session.setAttribute("tipoUsuario", "professor");
        dados.setPerfil("PROFESSORES");
        when(usuarios.consultarDadosPessoais(anyString())).thenReturn(dados);
        when(usuarios.possuiVinculosParaExclusao(42)).thenReturn(true);
        String html = mvc.perform(get("/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Matrícula")))
                .andExpect(content().string(containsString("Ver materiais")))
                .andExpect(content().string(not(containsString("Solicitar código de exclusão"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        preview("professor.html", html);
    }

    @Test
    void visitanteNaoAcessaDadosNemAcoes() throws Exception {
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
        for (String rota : new String[]{"exportar", "excluir", "excluir/confirmar", "excluir/cancelar"}) {
            mvc.perform(post("/profile/" + rota).with(csrf())).andExpect(redirectedUrl("/login"));
        }
        verifyNoInteractions(usuarios, emails);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exportar", "excluir", "excluir/confirmar", "excluir/cancelar"})
    void acoesExigemPostECsrf(String acao) throws Exception {
        mvc.perform(get("/profile/" + acao).session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/profile/" + acao).session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(usuarios, emails);
    }

    @Test
    void exportacaoIgnoraDestinatarioFornecidoPeloCliente() throws Exception {
        mvc.perform(post("/profile/exportar").session(session).with(csrf()).param("email", "vitima@example.com"))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("mensagemSucesso"));
        verify(emails).enviarEmailDados(dados);
    }

    @Test
    void falhaNoEmailNaoExibeSucesso() throws Exception {
        doThrow(new MailSendException("indisponivel")).when(emails).enviarEmailDados(dados);
        mvc.perform(post("/profile/exportar").session(session).with(csrf()))
                .andExpect(flash().attributeExists("mensagemErro")).andExpect(flash().attributeCount(1));
    }

    @Test
    void vinculosImpedemSolicitacaoDeExclusao() throws Exception {
        when(usuarios.possuiVinculosParaExclusao(42)).thenReturn(true);
        mvc.perform(post("/profile/excluir").session(session).with(csrf())).andExpect(flash().attributeExists("mensagemErro"));
        verifyNoInteractions(emails);
        verify(usuarios, never()).excluirDadosPessoais(anyLong(), anyString());
    }

    @Test
    void codigoCorretoEConfirmacaoExcluemContaEEncerramSessoes() throws Exception {
        String codigo = solicitarCodigo();
        String html = mvc.perform(get("/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("codigoExclusao")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        preview("confirmacao.html", html);
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                        .param("codigo", codigo).param("confirmar", "true"))
                .andExpect(redirectedUrl("/login?contaExcluida"));
        verify(usuarios).excluirDadosPessoais(42, "titular@example.com");
        verify(sessoes).encerrarSessoesDoUsuario("titular@example.com");
        assertTrue(session.isInvalid());
    }

    @Test
    void naoExcluiSemSolicitacaoOuSemConfirmacao() throws Exception {
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", "123456").param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        String codigo = solicitarCodigo();
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf()).param("codigo", codigo))
                .andExpect(flash().attributeExists("mensagemErro"));
        verify(usuarios, never()).excluirDadosPessoais(anyLong(), anyString());
    }

    @Test
    void limitaTentativasEReenvios() throws Exception {
        solicitarCodigo();
        mvc.perform(post("/profile/excluir").session(session).with(csrf())).andExpect(flash().attributeExists("mensagemErro"));
        verify(emails, times(1)).enviarCodigo(anyString(), anyString());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                    .param("codigo", "000000").param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        }
        assertNull(session.getAttribute("exclusaoPendente"));
        verify(usuarios, never()).excluirDadosPessoais(anyLong(), anyString());
    }

    @Test
    void codigoDeLoginNaoPodeExcluirContaECancelamentoInvalidaCodigo() throws Exception {
        String codigo = solicitarCodigo();
        String login = TwoFactorService.gerarCodigo(dados.getEmail());
        // Evita coincidência aleatória entre dois códigos válidos de seis dígitos.
        while (login.equals(codigo)) login = TwoFactorService.gerarCodigo(dados.getEmail());
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", login).param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        mvc.perform(post("/profile/excluir/cancelar").session(session).with(csrf())).andExpect(redirectedUrl("/profile"));
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", codigo).param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        verify(usuarios, never()).excluirDadosPessoais(anyLong(), anyString());
    }

    @Test
    void falhaNaExclusaoMantemSessaoSemDeclararSucesso() throws Exception {
        String codigo = solicitarCodigo();
        doThrow(new SQLException("indisponivel")).when(usuarios).excluirDadosPessoais(42, dados.getEmail());
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", codigo).param("confirmar", "true")).andExpect(status().isServiceUnavailable());
        assertFalse(session.isInvalid());
        verifyNoInteractions(sessoes);
    }

    @Test
    void contaQueNaoExisteMaisNaoAcessaPerfil() throws Exception {
        when(usuarios.consultarDadosPessoais(anyString())).thenReturn(null);
        mvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }

    private String solicitarCodigo() throws Exception {
        mvc.perform(post("/profile/excluir").session(session).with(csrf()))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("mensagemSucesso"));
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarCodigo(eq(dados.getEmail()), codigo.capture());
        return codigo.getValue();
    }

    private void preview(String nome, String html) throws Exception {
        if (Boolean.getBoolean("campuslink.profile.preview")) {
            Path pasta = Path.of("target", "profile-qa");
            Files.createDirectories(pasta);
            Files.writeString(pasta.resolve(nome), html);
        }
    }
}
