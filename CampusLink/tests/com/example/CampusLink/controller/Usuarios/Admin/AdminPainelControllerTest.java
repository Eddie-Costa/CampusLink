package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.support.AdminFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AdminPainelControllerTest {
    @Test void devePermitirAdministradorComSessaoValida() {
        assertEquals("Usuarios/Admin/painelAdmin", new AdminPainelController().painelAdmin(AdminFixture.sessao()));
    }
    @Test void deveAceitarEmailEquivalenteComEspacosEMaiusculas() {
        var session = AdminFixture.sessao(); session.setAttribute("email2FA", "  ADMIN-TESTE@EXAMPLE.COM ");
        assertEquals("Usuarios/Admin/painelAdmin", new AdminPainelController().painelAdmin(session));
    }
    @ParameterizedTest @ValueSource(strings = {"semSessao", "aluno", "emailDivergente", "emailAusente", "objetoInvalido", "emailVazio"})
    void deveRecusarSessaoInvalida(String caso) {
        var session = AdminFixture.sessao();
        switch (caso) {
            case "semSessao" -> session.clearAttributes();
            case "aluno" -> session.setAttribute("tipoUsuario", "aluno");
            case "emailDivergente" -> session.setAttribute("email2FA", "outro@example.com");
            case "emailAusente" -> session.removeAttribute("email2FA");
            case "objetoInvalido" -> session.setAttribute("usuarioLogado", "admin");
            default -> ((loginAdminDTO) session.getAttribute("usuarioLogado")).setEmail(" ");
        }
        assertEquals("redirect:/home", new AdminPainelController().painelAdmin(session));
    }
}
