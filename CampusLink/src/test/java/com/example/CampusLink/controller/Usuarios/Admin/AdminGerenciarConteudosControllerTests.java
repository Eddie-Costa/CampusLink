// verificacao do catalogo administrativo exercita filtros e limites de acesso
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// testes da lista de conteudos esta classe confere paginacao e recusa do perfil de aluno
class AdminGerenciarConteudosControllerTests {

    private final ConteudoService conteudoService = mock(ConteudoService.class);
    private final AdminGerenciarConteudosController controller = new AdminGerenciarConteudosController(conteudoService);

    // testar filtros administrativos de conteudos este teste confere busca situacao e ajuste de pagina fora do limite
    @Test
    void listaConteudosComBuscaFiltroEPaginacao() throws SQLException {

        loginAdminDTO admin = new loginAdminDTO();
        admin.setEmail("admin@example.com");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", admin);
        session.setAttribute("tipoUsuario", "admin");
        session.setAttribute("email2FA", admin.getEmail());
        ExtendedModelMap model = new ExtendedModelMap();
        List<ConteudoDTO> conteudos = List.of(new ConteudoDTO());

        when(conteudoService.contarFiltradosParaAdmin("matematica", "ativo")).thenReturn(11);
        when(conteudoService.listarTodosParaAdminPaginado(1, "matematica", "ativo")).thenReturn(conteudos);

        String pagina = controller.listarConteudos(session, model, 5, " matematica ", " ATIVO ", false);

        assertEquals("Usuarios/Admin/conteudosAdmin", pagina);
        assertEquals(conteudos, model.get("conteudos"));
        assertEquals("matematica", model.get("busca"));
        assertEquals("ativo", model.get("statusSelecionado"));
        assertEquals(11, model.get("totalFiltrados"));
        assertEquals(1, model.get("paginaAtual"));
        assertEquals(2, model.get("totalPaginas"));
        assertEquals(false, model.get("temMais"));
        verify(conteudoService).listarTodosParaAdminPaginado(1, "matematica", "ativo");
    }

    // testar bloqueio de aluno na lista administrativa este teste impede a consulta dos conteudos
    @Test
    void impedeAcessoDeAlunoSemConsultarConteudos() {

        loginAlunoDTO aluno = new loginAlunoDTO();
        aluno.setEmail("aluno@example.com");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", aluno);
        session.setAttribute("tipoUsuario", "aluno");
        session.setAttribute("email2FA", aluno.getEmail());

        String pagina = controller.listarConteudos(session, new ExtendedModelMap(), 0, "", "todos", false);

        assertEquals("redirect:/home", pagina);
        verifyNoInteractions(conteudoService);
    }
}
