package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.model.Evento;
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
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventosController {

    private static final Long ID_PROFESSOR_TESTE = 8L;
    private static final Long ID_TURMA_TESTE = 1L;

    private final EventoService eventoService;
    private final DisponibilidadeService disponibilidadeService;
    private final alunoDAO alunoDAO;

    @GetMapping
    public String exibirCalendario(
            Model model,
            HttpSession session
    ) throws SQLException {

        carregarDadosDaPagina(model, session);

        return "Geral/calendario-eventos";
    }

    @GetMapping("/cadastrar")
    public String exibirFormularioCadastro(
            Model model,
            HttpSession session
    ) throws SQLException {

        carregarDadosDaPagina(model, session);

        return "Geral/cadastrar-eventos";
    }

    @PostMapping("/cadastrar")
    public String cadastrarEvento(

            @RequestParam("nome")
            String nome,

            @RequestParam(
                    value = "tipo",
                    required = false
            )
            String tipo,

            @RequestParam(
                    value = "descricao",
                    required = false
            )
            String descricao,

            @RequestParam("inicio")
            @DateTimeFormat(
                    pattern = "yyyy-MM-dd'T'HH:mm"
            )
            LocalDateTime inicio,

            @RequestParam("fim")
            @DateTimeFormat(
                    pattern = "yyyy-MM-dd'T'HH:mm"
            )
            LocalDateTime fim,

            @RequestParam(
                    value = "prioridade",
                    required = false,
                    defaultValue = "MEDIA"
            )
            String prioridade,

            @RequestParam(
                    value = "conteudoId",
                    required = false
            )
            String conteudoId,

            @RequestParam(
                    value = "conteudoIds",
                    required = false
            )
            List<String> conteudoIds,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (!usuarioEhProfessor(session)) {
            return "redirect:/eventos";
        }

        if (nome == null || nome.isBlank()) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Informe o nome do evento."
            );

            return "redirect:/eventos/cadastrar";
        }

        if (fim.isBefore(inicio)) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "A data final não pode ser anterior à data inicial."
            );

            return "redirect:/eventos/cadastrar";
        }

        String conteudoSelecionado =
                conteudoIds == null || conteudoIds.isEmpty()
                        ? conteudoId
                        : String.join(", ", conteudoIds);

        Evento evento = new Evento(
                nome,
                inicio,
                fim,
                conteudoSelecionado,
                prioridade
        );

        evento.setTipo(tipo);
        evento.setDescricao(descricao);

        evento.setIdProfessor(
                ID_PROFESSOR_TESTE
        );

        evento.setIdTurma(
                ID_TURMA_TESTE
        );

        eventoService.adicionarEvento(evento);

        redirectAttributes.addFlashAttribute(
                "mensagem",
                "Evento cadastrado com sucesso!"
        );

        return "redirect:/eventos";
    }

    @PostMapping("/cadastrar-disponibilidade")
    public String cadastrarDisponibilidade(

            @RequestParam("data")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate data,

            @RequestParam("horasDisponiveis")
            Integer horasDisponiveis,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) throws SQLException {

        if (!usuarioEhAluno(session)) {
            return "redirect:/eventos";
        }

        Long idAluno =
                buscarIdAluno(session);

        if (idAluno == null) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Não foi possível identificar o aluno."
            );

            return "redirect:/eventos";
        }

        if (horasDisponiveis == null
                || horasDisponiveis < 0
                || horasDisponiveis > 24) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Informe uma quantidade entre 0 e 24 horas."
            );

            return "redirect:/eventos/cadastrar";
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

        return "redirect:/eventos";
    }

    private void carregarDadosDaPagina(
            Model model,
            HttpSession session
    ) throws SQLException {

        model.addAttribute(
                "eventos",
                eventoService.listarEventos()
        );

        model.addAttribute(
                "tipoUsuario",
                obterTipoUsuario(session)
        );

        model.addAttribute(
                "disponibilidades",
                List.of()
        );

        if (usuarioEhAluno(session)) {

            Long idAluno =
                    buscarIdAluno(session);

            if (idAluno != null) {

                model.addAttribute(
                        "disponibilidades",
                        disponibilidadeService
                                .listarPorAluno(idAluno)
                );
            }
        }
    }

    private Long buscarIdAluno(
            HttpSession session
    ) throws SQLException {

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

    private String obterTipoUsuario(
            HttpSession session
    ) {

        Object tipoUsuario =
                session.getAttribute("tipoUsuario");

        if (tipoUsuario == null) {
            return "";
        }

        return tipoUsuario.toString();
    }

    private boolean usuarioEhAluno(
            HttpSession session
    ) {
        return "aluno".equalsIgnoreCase(
                obterTipoUsuario(session)
        );
    }

    private boolean usuarioEhProfessor(
            HttpSession session
    ) {
        return "professor".equalsIgnoreCase(
                obterTipoUsuario(session)
        );
    }
}