// autenticacao para o spring security converte o login da sessao em um usuario com papel aluno professor ou admin
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

// controle da autenticacao por sessao esta classe informa ao spring security quem esta conectado e com qual papel
public class SessaoAutenticacaoFilter extends OncePerRequestFilter {

    // identificar usuario da requisicao este metodo le a sessao e registra o papel usado pelas regras do securityconfig
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        // ler sessao existente ignora visitantes sem sessao que seguem como anonimos
        if (session != null) {
            Object usuario = session.getAttribute("usuarioLogado");
            Object tipo = session.getAttribute("tipoUsuario");
            String email = null;
            String papel = null;

            // definir papel do usuario exige que os dados de login e o perfil escolhido sejam do mesmo tipo
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

            // registrar usuario no spring security cria a autenticacao somente quando o papel foi reconhecido
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
