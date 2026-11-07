package com.example.CampusLink.config;

import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.service.ChamadoAcessoService;
import com.example.CampusLink.service.TermosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

@Component
public class TermosInterceptor implements HandlerInterceptor {

    public static final String SESSAO_TERMOS_ACEITOS = "termosAceitos";
    public static final String SESSAO_TERMOS_PENDENTES = "termosPendentes";

    private static final Logger logger = LoggerFactory.getLogger(TermosInterceptor.class);
    private static final List<String> LIBERADOS_SEM_ACEITE = List.of(
            "/termos", "/logout", "/profile", "/css/", "/js/", "/images/", "/documentos/", "/error", "/favicon.ico");
    private static final Set<String> PAGINAS_PUBLICAS = Set.of(
            "/", "/home", "/funcionalidades", "/sobre-nos", "/login", "/cadastrar");

    private final ChamadoAcessoService acessoService;
    private final TermosService termosService;

    public TermosInterceptor(ChamadoAcessoService acessoService, TermosService termosService) {
        this.acessoService = acessoService;
        this.termosService = termosService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("usuarioLogado") == null
                || !("aluno".equals(session.getAttribute("tipoUsuario")) || "professor".equals(session.getAttribute("tipoUsuario")))
                || Boolean.TRUE.equals(session.getAttribute(SESSAO_TERMOS_ACEITOS))) {
            return true;
        }

        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (LIBERADOS_SEM_ACEITE.stream().anyMatch(caminho::startsWith)) {
            return true;
        }

        DadosUsuarioDTO usuario = acessoService.usuarioDaSessao(session);
        if (usuario == null) {
            return true;
        }

        try {
            if (termosService.aceitouVersaoAtual(usuario.getId())) {
                session.setAttribute(SESSAO_TERMOS_ACEITOS, true);
                session.removeAttribute(SESSAO_TERMOS_PENDENTES);
                return true;
            }
        } catch (IllegalStateException e) {
            logger.error("Não foi possível verificar o aceite dos termos. usuario={}", usuario.getId(), e);
            return true;
        }

        session.setAttribute(SESSAO_TERMOS_PENDENTES, true);
        if (PAGINAS_PUBLICAS.contains(caminho)) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/termos/aceite");
        return false;
    }
}
