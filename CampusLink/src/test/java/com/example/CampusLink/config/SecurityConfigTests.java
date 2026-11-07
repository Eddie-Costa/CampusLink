// verificacao das regras do spring security confere quem e levado ao login e quem recebe acesso negado
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

// testes das regras de acesso esta classe simula visitantes alunos professores e administradores em cada area
@WebMvcTest(TermosController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired MockMvc mvc;

    @MockitoBean TermosService termos;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean SessaoLogListener sessoes;

    // testar visitante nas areas de usuario este teste confere o envio ao login comum
    @Test
    void visitanteEhLevadoAoLoginNasAreasDeUsuario() throws Exception {
        mvc.perform(get("/turmas")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/eventos")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));
    }

    // testar visitante no backoffice este teste confere o envio ao login do administrador
    @Test
    void visitanteEhLevadoAoLoginDoAdminNoBackoffice() throws Exception {
        mvc.perform(get("/admin/painel")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/admin/dominios")).andExpect(redirectedUrl("/admin/login"));
    }

    // testar separacao dos perfis este teste confere acesso negado quando um perfil entra na area de outro
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

    // testar sessao pela metade este teste confere que cadastro ou login sem codigo 2fa nao vale como professor
    @Test
    void sessaoSemLoginCompletoNaoEhTratadaComoProfessor() throws Exception {
        MockHttpSession cadastroIniciado = new MockHttpSession();
        cadastroIniciado.setAttribute("tipoUsuario", "professor");
        cadastroIniciado.setAttribute("email2FA", "professor.real@teste.com");

        mvc.perform(post("/criarTurma").session(cadastroIniciado).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/arquivos/1/excluir").session(cadastroIniciado).with(csrf())).andExpect(redirectedUrl("/login"));
    }

    // testar termos publicos este teste confere que o texto dos termos abre sem login
    @Test
    void textoDosTermosContinuaPublico() throws Exception {
        String texto = "Termos";
        when(termos.versaoAtual()).thenReturn(new TermoVersaoDTO(1L, "1.0", texto,
                TermosService.calcularHash(texto), OffsetDateTime.now()));

        mvc.perform(get("/termos")).andExpect(status().isOk());
    }

    // preparar sessao de aluno este metodo simula um aluno com login completo
    private MockHttpSession aluno() {
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@teste.com");
        return sessao(aluno, "aluno");
    }

    // preparar sessao de professor este metodo simula um professor com login completo
    private MockHttpSession professor() {
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("prof@teste.com");
        return sessao(professor, "professor");
    }

    // preparar sessao de administrador este metodo simula um administrador com login completo
    private MockHttpSession admin() {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@teste.com");
        return sessao(admin, "admin");
    }

    // montar sessao do teste este metodo grava o usuario e o perfil na sessao simulada
    private MockHttpSession sessao(Object usuario, String tipo) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", usuario);
        session.setAttribute("tipoUsuario", tipo);
        return session;
    }
}
