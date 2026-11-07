// historico por pedido relaciona o acesso atual aos registros da sessao
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

// contexto de acesso esta classe identifica a sessao durante o pedido e limpa os dados ao terminar
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SessaoLogFilter extends OncePerRequestFilter {

    // registrar contexto da sessao este metodo associa os registros ao usuario durante cada pedido
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.remove("sessaoLogId");
        MDC.remove("aluno");
        MDC.remove("professor");
        MDC.remove("admin");
        MDC.remove("sessionId");

        // acompanhar solicitacao registra a sessao e remove seus dados do contexto ao terminar
        try {
            String caminho = request.getServletPath();
            boolean recursoEstatico = caminho.startsWith("/css/") || caminho.startsWith("/js/")
                    || caminho.startsWith("/images/") || caminho.equals("/favicon.ico");
            HttpSession session = request.getSession(!recursoEstatico);

            // identificar sessao existente associa o codigo e os dados do usuario ao registro da solicitacao
            if (session != null) {
                UUID sessaoLogId;
                synchronized (session) {
                    sessaoLogId = (UUID) session.getAttribute("sessaoLogId");
                    // criar codigo da sessao gera uma identificacao quando a sessao ainda nao possui uma
                    if (sessaoLogId == null) {
                        sessaoLogId = UUID.randomUUID();
                        session.setAttribute("sessaoLogId", sessaoLogId);
                    }
                }
                MDC.put("sessaoLogId", sessaoLogId.toString());


                String perfil = (String) session.getAttribute("tipoUsuario");
                String email = (String) session.getAttribute("email2FA");
                // registrar perfil autenticado inclui o email apenas para um usuario conectado com perfil conhecido
                if (session.getAttribute("usuarioLogado") != null && email != null
                        && ("aluno".equals(perfil) || "professor".equals(perfil) || "admin".equals(perfil))) {
                    MDC.put(perfil, email);
                }
            }

            filterChain.doFilter(request, response);
        } finally {

            MDC.remove("sessaoLogId");
            MDC.remove("aluno");
            MDC.remove("professor");
            MDC.remove("admin");
            MDC.remove("sessionId");
        }
    }
}
