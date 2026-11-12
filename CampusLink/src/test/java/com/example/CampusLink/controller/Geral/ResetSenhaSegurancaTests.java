// verificacao da recuperacao de senha confere que a troca exige o codigo validado do proprio email
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.LoginAttemptService;
import com.example.CampusLink.service.TermosService;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// testes do esqueci a senha esta classe simula tentativas de trocar a senha de outra conta
@Tag("integration")
@WebMvcTest(ResetPaswordController.class)
@Import(SecurityConfig.class)
class ResetSenhaSegurancaTests {

    private static final String SENHA_FORTE = "NovaSenha1!";

    @Autowired MockMvc mvc;

    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean LoginAttemptService tentativas;
    @MockitoBean TwoFactorService doisFatores;
    @MockitoBean emailService emails;
    @MockitoBean SessaoLogListener sessoes;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean TermosService termos;

    // testar troca sem codigo este teste confere que nao e possivel trocar a senha pulando o codigo do email
    @Test
    void naoTrocaSenhaDeOutraContaPulandoOCodigoDoEmail() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/ResetVerification").session(session).with(csrf()).param("email", "vitima@teste.com"));
        mvc.perform(post("/reset").session(session).with(csrf())
                        .param("senha", SENHA_FORTE).param("confirmarSenha", SENHA_FORTE))
                .andExpect(redirectedUrl("/ResetPassword_Verification"));

        verify(usuarios, never()).UpdateSenhaUsuario(any(), any());
    }

    // testar tela de nova senha este teste confere que ela exige o codigo validado
    @Test
    void telaDeNovaSenhaExigeCodigoValidado() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("email2FA", "vitima@teste.com");

        mvc.perform(get("/ResetPassword").session(session))
                .andExpect(redirectedUrl("/ResetPassword_Verification"));
    }

    // testar email diferente este teste confere que a liberacao de um email nao vale para outro
    @Test
    void liberacaoDeUmEmailNaoValeParaOutro() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ResetPaswordController.SESSAO_RESET_LIBERADO, "eu@teste.com");
        session.setAttribute("email2FA", "vitima@teste.com");

        mvc.perform(post("/reset").session(session).with(csrf())
                        .param("senha", SENHA_FORTE).param("confirmarSenha", SENHA_FORTE))
                .andExpect(redirectedUrl("/ResetPassword_Verification"));

        verify(usuarios, never()).UpdateSenhaUsuario(any(), any());
    }

    // testar troca valida este teste confere a troca apos o codigo e a limpeza da sessao
    @Test
    void trocaSenhaDepoisDoCodigoValidadoELimpaASessao() throws Exception {
        MockHttpSession session = liberada("eu@teste.com");

        mvc.perform(post("/reset").session(session).with(csrf())
                        .param("senha", SENHA_FORTE).param("confirmarSenha", SENHA_FORTE))
                .andExpect(redirectedUrl("/login"));

        verify(usuarios).UpdateSenhaUsuario(any(), eq("eu@teste.com"));
        assertNull(session.getAttribute(ResetPaswordController.SESSAO_RESET_LIBERADO));
        assertNull(session.getAttribute("email2FA"));
    }

    // testar senha fraca este teste confere que a senha fora das regras e recusada
    @Test
    void recusaSenhaFraca() throws Exception {
        mvc.perform(post("/reset").session(liberada("eu@teste.com")).with(csrf())
                .param("senha", "123").param("confirmarSenha", "123"));

        verify(usuarios, never()).UpdateSenhaUsuario(any(), any());
    }

    // testar usuario conectado este teste confere que o esqueci a senha nao troca a identidade da sessao
    @Test
    void usuarioLogadoNaoTrocaOEmailDaSessaoPeloEsqueciASenha() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", new Object());
        session.setAttribute("email2FA", "eu@teste.com");

        mvc.perform(post("/ResetVerification").session(session).with(csrf()).param("email", "vitima@teste.com"))
                .andExpect(redirectedUrl("/profile"));

        assertEquals("eu@teste.com", session.getAttribute("email2FA"));
    }

    // montar sessao liberada este metodo simula o codigo ja validado para o email informado
    private MockHttpSession liberada(String email) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(ResetPaswordController.SESSAO_RESET_LIBERADO, email);
        session.setAttribute("email2FA", email);
        return session;
    }
}
