// acesso ao atendimento identifica o titular dos pedidos de ajuda
package com.example.CampusLink.service;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

// identidade dos chamados esta classe usa o usuario da sessao e confere se sua conta esta ativa
@Service
public class ChamadoAcessoService {

    private final ChamadoRepository chamadoRepository;

    // preparar acesso aos chamados este construtor recebe a consulta de usuarios ativos
    public ChamadoAcessoService(ChamadoRepository chamadoRepository) {
        this.chamadoRepository = chamadoRepository;
    }

    // validar titular dos chamados este metodo identifica o perfil pela conta conectada e confere seu cadastro ativo
    public DadosUsuarioDTO usuarioDaSessao(HttpSession session) {
        Object usuario = session.getAttribute("usuarioLogado");
        Object tipo = session.getAttribute("tipoUsuario");
        String email = null;


        // identificar aluno da sessao usa o email do aluno conectado
        if ("aluno".equals(tipo) && usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        }
        // identificar professor da sessao usa o email do professor conectado
        else if ("professor".equals(tipo) && usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        }
        // identificar administrador da sessao usa o email do administrador conectado
        else if ("admin".equals(tipo) && usuario instanceof loginAdminDTO admin) {
            email = admin.getEmail();
        }

        // validar email da sessao encerra a busca quando o email esta vazio
        if (email == null || email.isBlank()) return null;
        return chamadoRepository.buscarUsuarioAtivo(email, tipo.toString());
    }
}
