package com.example.CampusLink.controller.Usuarios.Aluno;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.LocalDate;

@Controller
@RequestMapping("/disponibilidade")
@RequiredArgsConstructor
public class DisponibilidadeController {

    private final alunoDAO alunoDAO;
    private final EventoService eventoService;
    private final DisponibilidadeService disponibilidadeService;

    @GetMapping
    public String exibirCalendario(
            Model model,
            HttpSession session
    ) throws SQLException {

        if (!usuarioPodeAcessar(session)) {
            return "redirect:/login";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            return "redirect:/login";
        }

        carregarDadosDoAluno(model, idAluno);

        return "Usuarios/Aluno/calendario-disponibilidade";
    }

    @GetMapping("/cadastrar")
    public String exibirFormulario(
            Model model,
            HttpSession session
    ) throws SQLException {

        if (!usuarioPodeAcessar(session)) {
            return "redirect:/login";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            return "redirect:/login";
        }

        carregarDadosDoAluno(model, idAluno);

        return "Usuarios/Aluno/cadastrar-disponibilidade";
    }

    @PostMapping("/cadastrar")
    public String cadastrarDisponibilidade(
            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate data,

            @RequestParam("horasDisponiveis")
            Integer horasDisponiveis,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) throws SQLException {

        if (!usuarioPodeAcessar(session)) {
            return "redirect:/login";
        }

        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            return "redirect:/login";
        }

        if (horasDisponiveis == null
                || horasDisponiveis < 0
                || horasDisponiveis > 24) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Informe uma quantidade entre 0 e 24 horas."
            );

            return "redirect:/disponibilidade/cadastrar";
        }

        disponibilidadeService.salvarDisponibilidade(
                idAluno,
                data,
                horasDisponiveis
        );

        redirectAttributes.addFlashAttribute(
                "mensagem",
                "Disponibilidade cadastrada com sucesso!"
        );

        return "redirect:/disponibilidade";
    }

    private boolean usuarioPodeAcessar(HttpSession session) {

        Object usuarioLogado =
                session.getAttribute("usuarioLogado");

        Object tipoUsuario =
                session.getAttribute("tipoUsuario");

        return usuarioLogado != null
                && tipoUsuario != null
                && "aluno".equalsIgnoreCase(
                tipoUsuario.toString()
        );
    }

    private Long buscarIdAluno(HttpSession session)
            throws SQLException {

        Object email =
                session.getAttribute("email2FA");

        if (email == null) {
            return null;
        }

        String idAluno =
                alunoDAO.buscarPorIDAluno(
                        email.toString()
                );

        if (idAluno == null || idAluno.isBlank()) {
            return null;
        }

        return Long.valueOf(idAluno);
    }

    private void carregarDadosDoAluno(
            Model model,
            Long idAluno
    ) {
        model.addAttribute(
                "eventos",
                eventoService.listarEventos()
        );

        model.addAttribute(
                "disponibilidades",
                disponibilidadeService
                        .listarPorAluno(idAluno)
        );
    }
}