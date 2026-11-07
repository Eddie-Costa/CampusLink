package com.example.CampusLink.config;

import com.example.CampusLink.controller.Geral.TermosController;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.TermoVersaoDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TermosController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired MockMvc mvc;

    @MockitoBean TermosService termos;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean SessaoLogListener sessoes;

    @Test
    void visitanteEhLevadoAoLoginNasAreasDeUsuario() throws Exception {
        mvc.perform(get("/turmas")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/eventos")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void visitanteEhLevadoAoLoginDoAdminNoBackoffice() throws Exception {
        mvc.perform(get("/admin/painel")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/admin/dominios")).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void cadaPerfilSoAcessaSuasAreas() throws Exception {
        mvc.perform(get("/admin/painel").session(aluno())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/painel").session(professor())).andExpect(status().isForbidden());
        mvc.perform(get("/score").session(professor())).andExpect(status().isForbidden());
        mvc.perform(get("/acompanhamento").session(aluno())).andExpect(status().isForbidden());
        mvc.perform(get("/meus-conteudos").session(aluno())).andExpect(status().isForbidden());
        mvc.perform(post("/criarTurma").session(aluno()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui.html").session(professor())).andExpect(status().isForbidden());
        mvc.perform(post("/termos/revogar").session(admin()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void sessaoSemLoginCompletoNaoEhTratadaComoProfessor() throws Exception {
        MockHttpSession cadastroIniciado = new MockHttpSession();
        cadastroIniciado.setAttribute("tipoUsuario", "professor");
        cadastroIniciado.setAttribute("email2FA", "professor.real@teste.com");

        mvc.perform(post("/criarTurma").session(cadastroIniciado).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/arquivos/1/excluir").session(cadastroIniciado).with(csrf())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void textoDosTermosContinuaPublico() throws Exception {
        String texto = "Termos";
        when(termos.versaoAtual()).thenReturn(new TermoVersaoDTO(1L, "1.0", texto,
                TermosService.calcularHash(texto), OffsetDateTime.now()));

        mvc.perform(get("/termos")).andExpect(status().isOk());
    }

    private MockHttpSession aluno() {
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@teste.com");
        return sessao(aluno, "aluno");
    }

    private MockHttpSession professor() {
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("prof@teste.com");
        return sessao(professor, "professor");
    }

    private MockHttpSession admin() {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@teste.com");
        return sessao(admin, "admin");
    }

    private MockHttpSession sessao(Object usuario, String tipo) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", usuario);
        session.setAttribute("tipoUsuario", tipo);
        return session;
    }
}
