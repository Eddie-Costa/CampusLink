package com.example.CampusLink.config;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Traduz o login feito pela sessão da aplicação para o Spring Security, que então aplica as regras de acesso.
public class SessaoAutenticacaoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object usuario = session.getAttribute("usuarioLogado");
            Object tipo = session.getAttribute("tipoUsuario");
            String email = null;
            String papel = null;

            if (usuario instanceof loginAlunoDTO aluno && "aluno".equals(tipo)) {
                email = aluno.getEmail();
                papel = "ROLE_ALUNO";
            } else if (usuario instanceof loginProfessorDTO professor && "professor".equals(tipo)) {
                email = professor.getEmail();
                papel = "ROLE_PROFESSOR";
            } else if (usuario instanceof loginAdminDTO admin && "admin".equals(tipo)) {
                email = admin.getEmail();
                papel = "ROLE_ADMIN";
            }

            if (papel != null && email != null) {
                SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        email, null, List.of(new SimpleGrantedAuthority(papel))));
                SecurityContextHolder.setContext(contexto);
            }
        }

        chain.doFilter(request, response);
    }
}
