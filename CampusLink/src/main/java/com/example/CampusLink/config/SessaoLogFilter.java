package com.example.CampusLink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SessaoLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.remove("sessaoLogId");
        MDC.remove("aluno");
        MDC.remove("professor");
        MDC.remove("sessionId");

        try {
            String caminho = request.getServletPath();
            boolean recursoEstatico = caminho.startsWith("/css/") || caminho.startsWith("/js/")
                    || caminho.startsWith("/images/") || caminho.equals("/favicon.ico");
            HttpSession session = request.getSession(!recursoEstatico);

            if (session != null) {
                UUID sessaoLogId;
                synchronized (session) {
                    sessaoLogId = (UUID) session.getAttribute("sessaoLogId");
                    if (sessaoLogId == null) {
                        sessaoLogId = UUID.randomUUID();
                        session.setAttribute("sessaoLogId", sessaoLogId);
                    }
                }
                MDC.put("sessaoLogId", sessaoLogId.toString());

                // O contexto pertence à requisição, não à thread que a executa.
                String perfil = (String) session.getAttribute("tipoUsuario");
                String email = (String) session.getAttribute("email2FA");
                if (session.getAttribute("usuarioLogado") != null && email != null
                        && ("aluno".equals(perfil) || "professor".equals(perfil))) {
                    MDC.put(perfil, email);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // O UUID continua disponível durante o logout, mesmo após invalidate().
            MDC.remove("sessaoLogId");
            MDC.remove("aluno");
            MDC.remove("professor");
            MDC.remove("sessionId");
        }
    }
}
