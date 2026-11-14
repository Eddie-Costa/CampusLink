package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.config.SecurityConfig;
import com.example.CampusLink.config.SessaoLogListener;
import com.example.CampusLink.dto.Admin.SessaoLogDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.SessaoLogService;
import com.example.CampusLink.service.TermosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSessaoLogsController.class)
@Import({SecurityConfig.class, SessaoLogService.class})
class AdminSessaoLogsControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean TermosService termos;
    @MockitoBean ChamadoAcessoService acessoChamados;
    @MockitoBean SessaoLogListener listener;

    @Test
    void visitanteAlunoProfessorESessaoIncompletaNaoConsultamLogs() throws Exception {
        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@teste.com");
        loginProfessorDTO professor = new loginProfessorDTO();
        professor.setEmail("professor@teste.com");
        MockHttpSession incompleta = new MockHttpSession();
        incompleta.setAttribute("tipoUsuario", "admin");
        incompleta.setAttribute("email2FA", "admin@teste.com");
        for (String caminho : List.of("/admin/logs-sessoes", "/admin/logs-sessoes/" + UUID.randomUUID())) {
            mvc.perform(get(caminho)).andExpect(redirectedUrl("/admin/login"));
            mvc.perform(get(caminho).session(sessao(aluno, "aluno"))).andExpect(status().isForbidden());
            mvc.perform(get(caminho).session(sessao(professor, "professor"))).andExpect(status().isForbidden());
            mvc.perform(get(caminho).session(incompleta)).andExpect(redirectedUrl("/admin/login"));
            mvc.perform(get(caminho).session(sessao(aluno, "admin"))).andExpect(redirectedUrl("/admin/login"));
        }
        verifyNoInteractions(usuarios);
    }

    @Test
    void renderizaListaMenuEFiltrosNosLinksDePaginacaoSemLogsCompletos() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        LocalDate fim = LocalDate.of(2026, 11, 12);
        SessaoLogDTO registro = registro();
        registro.setLogCompleto("TEXTO_NAO_DEVE_SER_CARREGADO_NA_LISTA");
        when(usuarios.contarSessoesEncerradas("Maria", 5L, inicio, fim, 1, 60)).thenReturn(21L);
        when(usuarios.listarSessoesEncerradas(1, "Maria", 5L, inicio, fim, 1, 60)).thenReturn(List.of(registro));
        mvc.perform(get("/admin/logs-sessoes").session(admin()).param("busca", " Maria ").param("pagina", "1")
                        .param("usuarioId", "5").param("dataInicial", "2026-11-01").param("dataFinal", "2026-11-12")
                        .param("duracaoMin", "1").param("duracaoMax", "60"))
                .andExpect(status().isOk()).andExpect(view().name("Usuarios/Admin/logsSessoesAdmin"))
                .andExpect(content().string(containsString("Logs de Sessões")))
                .andExpect(content().string(containsString("Maria")))
                .andExpect(content().string(containsString("Visualizar logs")))
                .andExpect(content().string(containsString("Página 2 de 3")))
                .andExpect(content().string(containsString("pagina=2&amp;busca=Maria&amp;usuarioId=5&amp;dataInicial=2026-11-01&amp;dataFinal=2026-11-12&amp;duracaoMin=1&amp;duracaoMax=60")))
                .andExpect(content().string(not(containsString("TEXTO_NAO_DEVE_SER_CARREGADO_NA_LISTA"))))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verify(usuarios, never()).buscarSessaoEncerrada(any());
    }

    @Test
    void renderizaDetalheEscapaHtmlEOcultaSegredosAntesDaResposta() throws Exception {
        SessaoLogDTO registro = registro();
        registro.setLogCompleto("2026-11-12 10:00:00+00 [INFO] <script>alert(1)</script>\n"
                + "2026-11-12 10:01:00+00 [DEBUG] token=SEGREDO_NAO_EXIBIR\n");
        when(usuarios.buscarSessaoEncerrada(registro.getSessaoLogId())).thenReturn(registro);
        mvc.perform(get("/admin/logs-sessoes/" + registro.getSessaoLogId()).session(admin()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Maria")))
                .andExpect(content().string(containsString("0h 30min 00s")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("Registro ocultado")))
                .andExpect(content().string(not(containsString("SEGREDO_NAO_EXIBIR"))))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void sessaoAtivaOuInexistenteNaoTemDetalhe() throws Exception {
        mvc.perform(get("/admin/logs-sessoes/" + UUID.randomUUID()).session(admin())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/logs-sessoes/id-invalido").session(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void listaVaziaEFiltrosInvalidosExibemMensagem() throws Exception {
        when(usuarios.listarSessoesEncerradas(0, "", null, null, null, null, null)).thenReturn(List.of());
        mvc.perform(get("/admin/logs-sessoes").session(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma sessão encerrada encontrada")));
        mvc.perform(get("/admin/logs-sessoes").session(admin()).param("duracaoMin", "20").param("duracaoMax", "10"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("mensagemErro"));
        mvc.perform(get("/admin/logs-sessoes").session(admin()).param("dataInicial", "nao-e-data"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void erroDoBancoNaoExibeDetalhesTecnicosNaResposta() throws Exception {
        when(usuarios.contarSessoesEncerradas("", null, null, null, null, null)).thenThrow(new SQLException("falha simulada"));
        mvc.perform(get("/admin/logs-sessoes").session(admin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Não foi possível carregar as sessões")))
                .andExpect(content().string(not(containsString("falha simulada"))));
    }

    private SessaoLogDTO registro() {
        SessaoLogDTO registro = new SessaoLogDTO();
        registro.setSessaoLogId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        registro.setUsuarioId(5L);
        registro.setNomeUsuario("Maria");
        registro.setInicio(LocalDateTime.of(2026, 11, 12, 9, 0));
        registro.setFim(registro.getInicio().plusMinutes(30));
        return registro;
    }

    private MockHttpSession admin() {
        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@teste.com");
        return sessao(admin, "admin");
    }

    private MockHttpSession sessao(Object usuario, String tipo) {
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", usuario);
        sessao.setAttribute("tipoUsuario", tipo);
        return sessao;
    }
}
