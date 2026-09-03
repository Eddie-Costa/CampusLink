package com.example.CampusLink.controller.Geral;


import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.service.TwoFactorService;
import com.example.CampusLink.service.emailService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.SQLException;

@Controller
public class VerificarController {

    private static final Logger logger = LoggerFactory.getLogger(VerificarController.class);

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private emailService emailService;

    @Autowired
    private usuarioDAO usuarioDAO;

    @GetMapping("/verificarAluno")
    public String paginaVerificacaoAluno(HttpSession session) {
        System.out.println("[LOGIN] GET /verificarAluno - abrindo verificacao 2FA");

        //Verificar se aluno passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            System.out.println("[LOGIN] email 2FA ausente - redirecionando para /loginAluno");
            return "redirect:/loginAluno";
        }

        System.out.println("[LOGIN] email 2FA encontrado - exibindo pagina de verificacao");
        return "Geral/verificar";
    }

    @GetMapping("/verificarProfessor")
    public String paginaVerificacaoProfessor(HttpSession session) {
        System.out.println("[LOGIN] GET /verificarProfessor - abrindo verificacao 2FA");

        //Verificar se professor passou pela pagina de login primeiro
        if (session.getAttribute("email2FA") == null) {
            System.out.println("[LOGIN] email 2FA ausente - redirecionando para /loginProfessor");
            return "redirect:/loginProfessor";
        }

        System.out.println("[LOGIN] email 2FA encontrado - exibindo pagina de verificacao");
        return "Geral/verificar";
    }

    @PostMapping("/verificar")
    public String verificarCodigo(@RequestParam String codigo, HttpSession session, Model model) throws SQLException {

        System.out.println("[LOGIN] POST /verificar - codigo 2FA recebido");
        if(session.getAttribute("redirect").equals("Login")){

            //Exibe no console diferente por tipo de usuario
            if(session.getAttribute("tipoUsuario").equals("aluno")){
                System.out.println("[LOGIN] fluxo identificado como LoginAluno");
            } else if(session.getAttribute("tipoUsuario").equals("professor")){
                System.out.println("[LOGIN] fluxo identificado como professor");
            }

            String email = (String) session.getAttribute("email2FA");

            //  Segurança
            if (email == null) {
                if(session.getAttribute("tipoUsuario").equals("aluno")){
                    System.out.println("[LOGIN] email 2FA invalido - redirecionando para /loginAluno");
                    logger.warn("Email invalido com valor: {}", email);
                    return "redirect:/loginAluno";
                } else if(session.getAttribute("tipoUsuario").equals("professor")){
                    System.out.println("[LOGIN] email 2FA invalido - redirecionando para /loginProfessor");
                    logger.warn("Email invalido com valor: {}", email);
                    return "redirect:/loginProfessor";
                }
            }

            if (twoFactorService.validarCodigo(email, codigo)) {
                if(session.getAttribute("tipoUsuario").equals("aluno")){
                    System.out.println("[LOGIN] codigo 2FA valido - buscando aluno");
                    MDC.put("aluno", email);
                    MDC.put("sessionId", session.getId());
                    logger.info("O aluno com email:" +email+ " passou na validação de token para login");
                } else if(session.getAttribute("tipoUsuario").equals("professor")){
                    System.out.println("[LOGIN] codigo 2FA valido - buscando professor");
                    MDC.put("professor", email);
                    MDC.put("sessionId", session.getId());
                    logger.info("O professor com email:" +email+ " passou na validação de token para login");
                }

                // BUSCA USUÁRIO REAL
                if(session.getAttribute("tipoUsuario").equals("aluno")){
                    loginAlunoDTO aluno = usuarioDAO.buscarPorEmailAluno(email);

                    // CRIA SESSÃO
                    session.setAttribute("usuarioLogado", aluno);
                } else if(session.getAttribute("tipoUsuario").equals("professor")){
                    loginProfessorDTO professor = usuarioDAO.buscarPorEmailProfessor(email);

                    // CRIA SESSÃO
                    session.setAttribute("usuarioLogado", professor);
                }

                session.setMaxInactiveInterval(900);
                System.out.println("[LOGIN] sessao criada - login concluido");
                logger.info("Sessão criada com sucesso | ID: {} | Email: {}", session.getId(), email);

                return "redirect:/home";
            }
            System.out.println("[LOGIN] codigo 2FA invalido no fluxo LoginAluno");
        }
//        else if (session.getAttribute("redirect").equals("ResetPassword")){
//
//            // Segurança: impedir acesso direto
//            if (session.getAttribute("email2FA") == null) {
//                logger.warn("Email invalido com valor: {}", session.getAttribute("email2FA"));
//                return "redirect:/ResetPassword_Verification";
//            }
//
//            String email = (String) session.getAttribute("email2FA");
//
//            if (twoFactorService.validarCodigo(email, codigo)) {
//                logger.info("O aluno com email:" +email+ " passou na validação de token para Reset de senha");
//
//                // BUSCA USUÁRIO REAL
//                loginAlunoDTO aluno = usuarioDAO.buscarPorEmailAluno(email);
//
//                return "redirect:/ResetPassword";
//            }else{
//                // Código inválido
//                model.addAttribute("erro", "Código inválido ou expirado");
//                logger.warn("Codigo 2FA inválido inserido para o aluno com email:" +email);
//                return "verificar";
//            }
//
//        }else if (session.getAttribute("redirect").equals("ExcluirDados")){
//
//            // Segurança: impedir acesso direto
//            if (session.getAttribute("email2FA") == null) {
//                logger.warn("Email invalido com valor: {}", session.getAttribute("email2FA"));
//                return "redirect:/ResetPassword_Verification";
//            }
//
//            String email = (String) session.getAttribute("email2FA");
//
//            if (twoFactorService.validarCodigo(email, codigo)) {
//                logger.info("O aluno com email:" +email+ " passou na validação de token para Excluir dados");
//
////                usuarioDAO.DeleteUser(email);
//
//                return "redirect:/logout";
//            }else{
//                // Código inválido
//                model.addAttribute("erro", "Código inválido ou expirado");
//                logger.warn("Codigo 2FA inválido inserido para o aluno com email:" +email);
//                return "verificar";
//            }
//        }


        // Código inválido Geral
        model.addAttribute("erro", "Código inválido ou expirado");
        logger.warn("Codigo 2FA inválido inserido para o aluno com email:" +session.getAttribute("email2FA"));
        return "Geral/verificar";
    }
}
