package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.model.Evento;
import com.example.CampusLink.service.EventoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventosController {

//    Problema - Riq
    private static final Long ID_PROFESSOR_TESTE = 8L;
    private static final Long ID_TURMA_TESTE = 1L;

    private final EventoService eventoService;

    @GetMapping
    public String exibirCalendario(Model model) {
        model.addAttribute("eventos", eventoService.listarEventos());
        return "Geral/calendario-eventos";
    }

    @GetMapping("/cadastrar")
    public String exibirFormularioCadastro(Model model) {
        model.addAttribute("eventos", eventoService.listarEventos());
        return "Geral/cadastrar-eventos";
    }

    @PostMapping("/cadastrar")
    public String cadastrarEvento(
            @RequestParam("nome") String nome,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam("inicio")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime inicio,
            @RequestParam("fim")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime fim,
            @RequestParam(
                    value = "prioridade",
                    required = false,
                    defaultValue = "MEDIA"
            )
            String prioridade,
            @RequestParam(value = "conteudoId", required = false)
            String conteudoId,
            @RequestParam(value = "conteudoIds", required = false)
            List<String> conteudoIds,
            RedirectAttributes redirectAttributes
    ) {
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
        evento.setIdProfessor(ID_PROFESSOR_TESTE);
        evento.setIdTurma(ID_TURMA_TESTE);

        eventoService.adicionarEvento(evento);

        redirectAttributes.addFlashAttribute(
                "mensagem",
                "Evento cadastrado com sucesso!"
        );

        return "redirect:/eventos";
    }
}