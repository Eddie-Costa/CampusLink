package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.service.DisponibilidadeService;
import com.example.CampusLink.service.EventoService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // registra as informacoes do controller
    private static final Logger logger =
            LoggerFactory.getLogger(EventosController.class);

    // ids usados durante os testes
    private static final Long ID_PROFESSOR_TESTE = 8L;
    private static final Long ID_TURMA_TESTE = 1L;

    // dependencias usadas pelo controller
    private final EventoService eventoService;
    private final DisponibilidadeService disponibilidadeService;
    private final alunoDAO alunoDAO;

    // mostra o calendario de eventos
    @GetMapping
    public String exibirCalendario(Model model, HttpSession session) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {
            logger.warn("Acesso recusado ao calendario: login ou 2FA nao concluido.");
            return "redirect:/login";
        }

        carregarDadosDaPagina(model, session);
        logger.info("Calendário de eventos exibido. tipoUsuario={}", obterTipoUsuario(session));
        return "Geral/calendario-eventos";
    }

    // mostra a tela de cadastro de eventos
    @GetMapping("/cadastrar")
    public String exibirFormularioCadastro(Model model, HttpSession session) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {
            logger.warn("Acesso recusado ao cadastro de evento: login ou 2FA nao concluido.");
            return "redirect:/login";
        }

        carregarDadosDaPagina(model, session);
        logger.info("Formulário de cadastro de evento exibido. tipoUsuario={}", obterTipoUsuario(session));
        return "Geral/cadastrar-eventos";
    }

    // cadastra um novo evento
    @PostMapping("/cadastrar")
    public String cadastrarEvento(

            @RequestParam("nome")
            String nome,

            @RequestParam(value = "tipo", required = false)
            String tipo,

            @RequestParam(value = "descricao", required = false)
            String descricao,

            @RequestParam("inicio")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime inicio,

            @RequestParam("fim")
            @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            LocalDateTime fim,

            @RequestParam(value = "prioridade", required = false, defaultValue = "MEDIA")
            String prioridade,

            @RequestParam(value = "conteudoId", required = false)
            String conteudoId,

            @RequestParam(value = "conteudoIds", required = false)
            List<String> conteudoIds,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (!usuarioPassouPeloLoginE2FA(session)) {
            logger.warn("Cadastro de evento recusado: login ou 2FA nao concluido.");
            return "redirect:/login";
        }

        // verifica se o usuario e professor
        if (!usuarioEhProfessor(session)) {
            logger.warn("Cadastro de evento recusado: usuário sem perfil de professor.");
            return "redirect:/eventos";
        }

        // verifica se o nome foi informado
        if (nome == null || nome.isBlank()) {
            logger.warn("Cadastro de evento recusado: nome do evento vazio.");
            redirectAttributes.addFlashAttribute("erro", "Informe o nome do evento.");
            return "redirect:/eventos/cadastrar";
        }

        // verifica se as datas estao corretas
        if (fim.isBefore(inicio)) {
            logger.warn("Cadastro de evento recusado: data final anterior ao início. inicio={} fim={}", inicio, fim);
            redirectAttributes.addFlashAttribute("erro", "A data final não pode ser anterior à data inicial.");
            return "redirect:/eventos/cadastrar";
        }

        // junta os ids dos conteudos selecionados
        String conteudoSelecionado =
                conteudoIds == null || conteudoIds.isEmpty()
                        ? conteudoId
                        : String.join(
                        ", ",
                        conteudoIds
                );

        // cria o objeto do evento
        Evento evento = new Evento(nome, inicio, fim, conteudoSelecionado, prioridade);

        evento.setTipo(tipo);
        evento.setDescricao(descricao);

        // define os ids usados no teste
        evento.setIdProfessor(ID_PROFESSOR_TESTE);
        evento.setIdTurma(ID_TURMA_TESTE);

        // salva o evento no banco
        eventoService.adicionarEvento(evento);
        logger.info("Solicitação de cadastro de evento concluída.");
        redirectAttributes.addFlashAttribute("mensagem", "Evento cadastrado com sucesso!");
        return "redirect:/eventos";
    }

    // cadastra a disponibilidade do aluno
    @PostMapping("/cadastrar-disponibilidade")
    public String cadastrarDisponibilidade(

            @RequestParam("data")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate data,

            @RequestParam("horasDisponiveis")
            Integer horasDisponiveis,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) throws SQLException {

        if (!usuarioPassouPeloLoginE2FA(session)) {
            logger.warn("Cadastro de disponibilidade recusado: login ou 2FA nao concluido.");
            return "redirect:/login";
        }

        // verifica se o usuario e aluno
        if (!usuarioEhAluno(session)) {
            logger.warn("Cadastro de disponibilidade recusado: usuário sem perfil de aluno.");
            return "redirect:/eventos";
        }

        // busca o id do aluno logado
        Long idAluno = buscarIdAluno(session);

        if (idAluno == null) {
            logger.warn("Cadastro de disponibilidade recusado: aluno não identificado.");
            redirectAttributes.addFlashAttribute("erro", "Não foi possível identificar o aluno.");
            return "redirect:/eventos";
        }

        // verifica se a quantidade de horas e valida
        if (horasDisponiveis == null || horasDisponiveis < 0 || horasDisponiveis > 24) {
            logger.warn("Disponibilidade recusada. alunoId={} data={} horas={}", idAluno, data, horasDisponiveis);
            redirectAttributes.addFlashAttribute("erro", "Informe uma quantidade entre 0 e 24 horas.");
            return "redirect:/eventos/cadastrar";
        }

        // salva a disponibilidade do aluno
        disponibilidadeService.salvarDisponibilidade(idAluno, data, horasDisponiveis);
        logger.info("Disponibilidade cadastrada. alunoId={} data={} horas={}", idAluno, data, horasDisponiveis);
        redirectAttributes.addFlashAttribute("mensagem", "Disponibilidade cadastrada com sucesso!");
        return "redirect:/eventos";
    }

    // carrega os dados usados nas telas do calendario
    private void carregarDadosDaPagina(Model model, HttpSession session) throws SQLException {
        String tipoUsuario = obterTipoUsuario(session);
        logger.debug("Carregando dados do calendário. tipoUsuario={}", tipoUsuario);
        model.addAttribute("eventos", eventoService.listarEventos());
        model.addAttribute("tipoUsuario", tipoUsuario);
        model.addAttribute("disponibilidades", List.of());

        // carrega disponibilidades somente para alunos
        if (usuarioEhAluno(session)) {
            Long idAluno = buscarIdAluno(session);

            if (idAluno != null) {
                model.addAttribute("disponibilidades", disponibilidadeService.listarPorAluno(idAluno));
                logger.debug("Disponibilidades do aluno carregadas. alunoId={}", idAluno);
            }
        }

        logger.debug("Dados do calendário carregados. tipoUsuario={}", tipoUsuario);
    }

    // busca o id do aluno usando o email salvo na sessao
    private Long buscarIdAluno(HttpSession session) throws SQLException {

        Object email = session.getAttribute("email2FA");

        if (email == null) {
            logger.warn("Não foi possível buscar o aluno: email2FA ausente na sessão.");
            return null;
        }

        String idAluno = alunoDAO.buscarPorIDAluno(email.toString());

        if (idAluno == null || idAluno.isBlank()) {
            logger.warn("Nenhum aluno foi encontrado para a sessão atual.");
            return null;
        }

        logger.debug("Aluno identificado para o calendário. alunoId={}", idAluno);
        return Long.valueOf(idAluno);
    }

    // verifica se o login e o 2fa foram concluidos
    private boolean usuarioPassouPeloLoginE2FA(HttpSession session) {
        return session.getAttribute("usuarioLogado") != null
                && session.getAttribute("email2FA") != null
                && !obterTipoUsuario(session).isBlank();
    }

    // retorna o tipo do usuario salvo na sessao
    private String obterTipoUsuario(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");

        if (tipoUsuario == null) {return "";}
        return tipoUsuario.toString();
    }

    // verifica se o usuario e aluno
    private boolean usuarioEhAluno(HttpSession session) {
        return "aluno".equalsIgnoreCase(obterTipoUsuario(session));
    }

    // verifica se o usuario e professor
    private boolean usuarioEhProfessor(HttpSession session) {
        return "professor".equalsIgnoreCase(obterTipoUsuario(session));
    }
}