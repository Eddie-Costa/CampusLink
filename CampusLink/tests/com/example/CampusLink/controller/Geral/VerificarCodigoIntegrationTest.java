package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.service.TwoFactorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificarCodigoIntegrationTest {

    private static final String EMAIL = "integracao.2fa.invalido@campuslink.invalid";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TwoFactorService twoFactorService;

    private MockHttpSession session;

    @BeforeEach
    void prepararVerificacaoPendente() {
        twoFactorService.limparCodigo(EMAIL);
        twoFactorService.gerarCodigo(EMAIL);

        session = new MockHttpSession();
        session.setAttribute("email2FA", EMAIL);
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("redirect", "Login");
    }

    @AfterEach
    void limparCodigoTemporario() {
        twoFactorService.limparCodigo(EMAIL);
    }

    @Test
    void postVerificar_deveExibirErroEManterVerificacaoQuandoCodigoForIncorreto() throws Exception {
        mockMvc.perform(post("/verificar")
                        .session(session)
                        .with(csrf())
                        .param("codigo", "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("Geral/verificar"))
                .andExpect(model().attribute("erro", "codigo invalido tente novamente"))
                .andExpect(content().string(containsString("codigo invalido tente novamente")))
                .andExpect(request().sessionAttribute("email2FA", EMAIL))
                .andExpect(request().sessionAttribute("tipoUsuario", "aluno"))
                .andExpect(request().sessionAttributeDoesNotExist("usuarioLogado"));

        assertTrue(twoFactorService.obterExpiracaoCodigo(EMAIL) > System.currentTimeMillis());
    }
}
