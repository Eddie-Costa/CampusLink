package com.example.CampusLink.support;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import org.springframework.mock.web.MockHttpSession;

public final class AdminFixture {
    public static final String LOGIN = "redirect:/gestao/8f3c1d7a-2b94-4e61-a5c8-7d2f9b4a6e31";
    private AdminFixture() {}
    public static MockHttpSession sessao() {
        var session = new MockHttpSession();
        var admin = new loginAdminDTO();
        admin.setEmail("admin-teste@example.com");
        session.setAttribute("usuarioLogado", admin);
        session.setAttribute("email2FA", admin.getEmail());
        session.setAttribute("tipoUsuario", "admin");
        return session;
    }
}
