package com.example.CampusLink.service;

import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.DadosUsuarioDTO;
import com.example.CampusLink.repository.ChamadoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class ChamadoAcessoService {

    private final ChamadoRepository chamadoRepository;

    public ChamadoAcessoService(ChamadoRepository chamadoRepository) {
        this.chamadoRepository = chamadoRepository;
    }

    public DadosUsuarioDTO usuarioDaSessao(HttpSession session) {
        Object usuario = session.getAttribute("usuarioLogado");
        Object tipo = session.getAttribute("tipoUsuario");
        String email = null;

        // usa o usuario autenticado, pois o email do 2FA pode pertencer a outro fluxo
        if ("aluno".equals(tipo) && usuario instanceof loginAlunoDTO aluno) {
            email = aluno.getEmail();
        } else if ("professor".equals(tipo) && usuario instanceof loginProfessorDTO professor) {
            email = professor.getEmail();
        } else if ("admin".equals(tipo) && usuario instanceof loginAdminDTO admin) {
            email = admin.getEmail();
        }

        if (email == null || email.isBlank()) return null;
        return chamadoRepository.buscarUsuarioAtivo(email, tipo.toString());
    }
}
