package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.repository.DashboardRepository;
import com.example.CampusLink.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardControllerTests {
    private final DashboardRepository repository = mock(DashboardRepository.class);
    private final DashboardService service = mock(DashboardService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardController(repository, service)).build();

    public static MockHttpSession sessao(String perfil) {
        MockHttpSession session = new MockHttpSession();
        String email = perfil + "@example.com";
        Object usuario;
        if (perfil.equals("aluno")) {
            var aluno = new loginAlunoDTO(); aluno.setEmail(email); usuario = aluno;
        } else if (perfil.equals("professor")) {
            var professor = new loginProfessorDTO(); professor.setEmail(email); usuario = professor;
        } else {
            var admin = new loginAdminDTO(); admin.setEmail(email); usuario = admin;
        }
        session.setAttribute("usuarioLogado", usuario);
        session.setAttribute("tipoUsuario", perfil);
        session.setAttribute("email2FA", email);
        return session;
    }

    @ParameterizedTest
    @CsvSource({"aluno,professor", "aluno,admin", "professor,aluno", "professor,admin", "admin,aluno", "admin,professor"})
    void bloqueiaOutrosPerfisAntesDeConsultarBanco(String origem, String destino) throws Exception {
        mvc.perform(get("/dashboard/" + destino).session(sessao(origem))).andExpect(status().isForbidden());
        verifyNoInteractions(repository, service);
    }

    @ParameterizedTest
    @CsvSource({"aluno", "professor", "admin"})
    void direcionaMenuEExigeLoginCompleto(String tipo) throws Exception {
        mvc.perform(get("/dashboard").session(sessao(tipo))).andExpect(redirectedUrl("/dashboard/" + tipo));
        mvc.perform(get("/dashboard/" + tipo)).andExpect(redirectedUrl("/login"));
        var incompleta = sessao(tipo);
        incompleta.removeAttribute("usuarioLogado");
        mvc.perform(get("/dashboard/" + tipo).session(incompleta)).andExpect(redirectedUrl("/login"));
        var inconsistente = sessao(tipo);
        inconsistente.setAttribute("email2FA", "outra@example.com");
        mvc.perform(get("/dashboard/" + tipo).session(inconsistente)).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(repository, service);
    }

    @Test
    void bloqueiaTurmaAlheiaEContaInativa() throws Exception {
        when(repository.buscarIdentidade("aluno@example.com", "aluno")).thenReturn(10L);
        when(repository.listarTurmas(any())).thenReturn(List.of(Map.of("id", 100L, "nome", "Minha turma")));
        mvc.perform(get("/dashboard/aluno?turma=999").session(sessao("aluno"))).andExpect(status().isForbidden());
        when(repository.buscarIdentidade("aluno@example.com", "aluno")).thenReturn(null);
        mvc.perform(get("/dashboard/aluno").session(sessao("aluno"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void filtrosInvalidosNaoViraramSqlOuIdentidade() throws Exception {
        when(repository.buscarIdentidade("aluno@example.com", "aluno")).thenReturn(10L);
        when(repository.listarTurmas(any())).thenReturn(List.of());
        when(service.aluno(eq(10L), any())).thenReturn(Map.of());
        mvc.perform(get("/dashboard/aluno?periodo=invalido&prioridade='&pessoa=20&perfil=ADMINISTRADOR")
                .session(sessao("aluno"))).andExpect(status().isOk()).andExpect(view().name("Dashboards/aluno"));
        verify(service).aluno(eq(10L), argThat(f -> f.getPeriodo().equals("todos") && f.getPrioridade().equals("todos")));
        mvc.perform(get("/dashboard/aluno?turma=abc").session(sessao("aluno"))).andExpect(status().isBadRequest());
    }
}
