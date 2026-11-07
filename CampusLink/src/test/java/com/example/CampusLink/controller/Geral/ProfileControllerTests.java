// verificacao de perfil exercita consulta exportacao e exclusao da propria conta
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.dto.FinalidadeCodigoEmail;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
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

// testes dos dados pessoais esta classe confere identidade protecao dos pedidos codigos e tratamento de falhas
@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, TwoFactorService.class})
class ProfileControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean emailService emails;
    @MockitoBean SessaoLogListener sessoes;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean TermosService termos;
    private MockHttpSession session;
    private DadosUsuarioDTO dados;

    // preparar testes do perfil este metodo monta o titular e simula a consulta de seus dados
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

    // testar dados do titular na tela este teste confere a protecao do formulario e a ausencia de dados indevidos
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

    // testar perfil com vinculos este teste confere que o professor com materiais pode encerrar a conta e ve que o material permanece
    @Test
    void renderizaProfessorComVinculosInformandoQueOMaterialPermanece() throws Exception {
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("titular@example.com");
        session.setAttribute("usuarioLogado", professor);
        session.setAttribute("tipoUsuario", "professor");
        dados.setPerfil("PROFESSORES");
        when(usuarios.consultarDadosPessoais(anyString())).thenReturn(dados);
        when(usuarios.possuiVinculosParaExclusao(42)).thenReturn(true);
        String html = mvc.perform(get("/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Matrícula")))
                .andExpect(content().string(containsString("continuarão disponíveis para os alunos")))
                .andExpect(content().string(containsString("Solicitar código de exclusão")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        preview("professor.html", html);
    }

    // testar visitante no perfil este teste confere que dados e acoes exigem login
    @Test
    void visitanteNaoAcessaDadosNemAcoes() throws Exception {
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
        for (String rota : new String[]{"exportar", "excluir", "excluir/confirmar", "excluir/cancelar"}) {
            mvc.perform(post("/profile/" + rota).with(csrf())).andExpect(redirectedUrl("/login"));
        }
        verifyNoInteractions(usuarios, emails);
    }

    // testar administrador no perfil pessoal este teste confere o redirecionamento das paginas e acoes para o painel
    @Test
    void administradorNaoAcessaPerfilNemAcoesDeDadosPessoais() throws Exception {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@example.com");
        session.setAttribute("usuarioLogado", admin);
        session.setAttribute("tipoUsuario", "admin");

        mvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/admin/painel"));
        mvc.perform(get("/profile/dados").session(session)).andExpect(redirectedUrl("/admin/painel"));
        for (String rota : new String[]{"exportar", "excluir", "excluir/confirmar", "excluir/cancelar"}) {
            mvc.perform(post("/profile/" + rota).session(session).with(csrf()))
                    .andExpect(redirectedUrl("/admin/painel"));
        }

        verifyNoInteractions(usuarios, emails);
    }

    // testar protecao das acoes pessoais este teste exige envio de formulario com a protecao contra pedidos indevidos
    @ParameterizedTest
    @ValueSource(strings = {"exportar", "excluir", "excluir/confirmar", "excluir/cancelar"})
    void acoesExigemPostECsrf(String acao) throws Exception {
        mvc.perform(get("/profile/" + acao).session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/profile/" + acao).session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(usuarios, emails);
    }

    // testar destinatario da exportacao este teste confere que o email enviado pelo navegador nao troca o titular
    @Test
    void exportacaoIgnoraDestinatarioFornecidoPeloCliente() throws Exception {
        mvc.perform(post("/profile/exportar").session(session).with(csrf()).param("email", "vitima@example.com"))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("mensagemSucesso"));
        verify(emails).enviarEmailDados(dados);
    }

    // testar falha no email de dados este teste exige aviso de erro sem mensagem de sucesso
    @Test
    void falhaNoEmailNaoExibeSucesso() throws Exception {
        doThrow(new MailSendException("indisponivel")).when(emails).enviarEmailDados(dados);
        mvc.perform(post("/profile/exportar").session(session).with(csrf()))
                .andExpect(flash().attributeExists("mensagemErro")).andExpect(flash().attributeCount(1));
    }

    // testar vinculos antes da anonimizacao este teste confere que responsabilidades nao impedem o envio do codigo
    @Test
    void vinculosNaoImpedemSolicitacaoDeExclusao() throws Exception {
        when(usuarios.possuiVinculosParaExclusao(42)).thenReturn(true);
        mvc.perform(post("/profile/excluir").session(session).with(csrf()))
                .andExpect(flash().attributeExists("mensagemSucesso"));
        verify(emails).enviarCodigo(eq("titular@example.com"), anyString(), eq(FinalidadeCodigoEmail.EXCLUSAO_CONTA));
    }

    // testar encerramento confirmado este teste valida a anonimizacao o registro e o encerramento das sessoes
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
        verify(usuarios).anonimizarDadosPessoais(42, "titular@example.com");
        verify(usuarios).InserirLogsNoBD(isNull(), eq("INFO"), anyString(), eq("confirmarExclusao"),
                isNull(), eq("Conta excluída. usuarioId=42"), isNull(), isNull());
        verify(sessoes).encerrarSessoesDoUsuario("titular@example.com");
        assertTrue(session.isInvalid());
    }

    // testar exclusao sem confirmacao este teste impede remover a conta sem pedido previo ou aceite explicito
    @Test
    void naoExcluiSemSolicitacaoOuSemConfirmacao() throws Exception {
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", "123456").param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        String codigo = solicitarCodigo();
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf()).param("codigo", codigo))
                .andExpect(flash().attributeExists("mensagemErro"));
        verify(usuarios, never()).anonimizarDadosPessoais(anyLong(), anyString());
    }

    // testar limites da exclusao este teste verifica a espera para reenvio e o bloqueio apos cinco codigos errados
    @Test
    void limitaTentativasEReenvios() throws Exception {
        solicitarCodigo();
        mvc.perform(post("/profile/excluir").session(session).with(csrf())).andExpect(flash().attributeExists("mensagemErro"));
        verify(emails, times(1)).enviarCodigo(anyString(), anyString(), eq(FinalidadeCodigoEmail.EXCLUSAO_CONTA));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                    .param("codigo", "000000").param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        }
        assertNull(session.getAttribute("exclusaoPendente"));
        verify(usuarios).InserirLogsNoBD(isNull(), eq("WARN"), anyString(), eq("confirmarExclusao"),
                isNull(), contains("Limite de tentativas de exclusão atingido"), isNull(), isNull());
        verify(usuarios, never()).anonimizarDadosPessoais(anyLong(), anyString());
    }

    // testar separacao dos codigos este teste impede usar codigo de login ou codigo de exclusao cancelado
    @Test
    void codigoDeLoginNaoPodeExcluirContaECancelamentoInvalidaCodigo() throws Exception {
        String codigo = solicitarCodigo();
        String login = TwoFactorService.gerarCodigo(dados.getEmail());

        while (login.equals(codigo)) login = TwoFactorService.gerarCodigo(dados.getEmail());
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", login).param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        mvc.perform(post("/profile/excluir/cancelar").session(session).with(csrf())).andExpect(redirectedUrl("/profile"));
        verify(usuarios).InserirLogsNoBD(isNull(), eq("INFO"), anyString(), eq("cancelarExclusao"),
                isNull(), contains("cancelada. usuarioId=42"), isNull(), isNull());
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", codigo).param("confirmar", "true")).andExpect(flash().attributeExists("mensagemErro"));
        verify(usuarios, never()).anonimizarDadosPessoais(anyLong(), anyString());
    }

    // testar falha ao anonimizar conta este teste preserva a sessao e registra o erro sem declarar sucesso
    @Test
    void falhaNaExclusaoMantemSessaoSemDeclararSucesso() throws Exception {
        String codigo = solicitarCodigo();
        doThrow(new SQLException("indisponivel")).when(usuarios).anonimizarDadosPessoais(42, dados.getEmail());
        mvc.perform(post("/profile/excluir/confirmar").session(session).with(csrf())
                .param("codigo", codigo).param("confirmar", "true")).andExpect(status().isServiceUnavailable());
        assertFalse(session.isInvalid());
        verify(usuarios).InserirLogsNoBD(isNull(), eq("ERROR"), anyString(), eq("erroBanco"),
                isNull(), anyString(), isNull(), contains("SQLException: indisponivel"));
        verifyNoInteractions(sessoes);
    }

    // testar perfil de conta ausente este teste encerra a sessao quando o titular nao existe mais
    @Test
    void contaQueNaoExisteMaisNaoAcessaPerfil() throws Exception {
        when(usuarios.consultarDadosPessoais(anyString())).thenReturn(null);
        mvc.perform(get("/profile").session(session)).andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
    }

    // solicitar codigo no teste este metodo executa o pedido e captura o codigo enviado ao titular
    private String solicitarCodigo() throws Exception {
        mvc.perform(post("/profile/excluir").session(session).with(csrf()))
                .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("mensagemSucesso"));
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emails).enviarCodigo(eq(dados.getEmail()), codigo.capture(), eq(FinalidadeCodigoEmail.EXCLUSAO_CONTA));
        return codigo.getValue();
    }

    // salvar previa do perfil este metodo grava a pagina de teste somente quando a opcao esta habilitada
    private void preview(String nome, String html) throws Exception {
        // salvar previa do perfil grava o html apenas quando a opcao de previa esta habilitada
        if (Boolean.getBoolean("campuslink.profile.preview")) {
            Path pasta = Path.of("target", "profile-qa");
            Files.createDirectories(pasta);
            Files.writeString(pasta.resolve(nome), html);
        }
    }
}
