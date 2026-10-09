
package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.dao.AdminTurmaDAO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.Admin.usuarioAdminDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.model.Evento;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.EventoService;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
public class AdminEventosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEventosController.class);

    private final EventoService eventoService;
    private final AdminTurmaDAO adminTurmaDAO;
    private final ConteudoService conteudoService;
    private final JdbcTemplate jdbcTemplate;

    public AdminEventosController(EventoService eventoService, AdminTurmaDAO adminTurmaDAO,
                                  ConteudoService conteudoService, JdbcTemplate jdbcTemplate) {
        this.eventoService = eventoService;
        this.adminTurmaDAO = adminTurmaDAO;
        this.conteudoService = conteudoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // lista todos os eventos cadastrados
    @GetMapping("/admin/eventos")
    public String listarEventos(HttpSession session, Model model) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            List<Evento> eventos = eventoService.listarEventos();
            model.addAttribute("eventos", eventos);

        } catch (RuntimeException e) {
            logger.error("erro ao carregar eventos para o administrador", e);
            model.addAttribute("mensagemErro", "Não foi possível carregar os eventos.");
            model.addAttribute("eventos", new ArrayList<Evento>());
        }

        return "Usuarios/Admin/eventosAdmin";
    }

    // mostra o formulario para cadastrar eventos
    @GetMapping("/admin/eventos/cadastrar")
    public String exibirCadastroEvento(HttpSession session, Model model) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            model.addAttribute("turmas", adminTurmaDAO.listarTurmas());

        } catch (SQLException e) {
            logger.error("erro ao carregar turmas para cadastro de evento", e);
            model.addAttribute("turmas", new ArrayList<>());
            model.addAttribute("mensagemErro", "Não foi possível carregar as turmas.");
        }

        return "Usuarios/Admin/cadastrarEventoAdmin";
    }

    // busca os professores da turma escolhida
    @GetMapping("/admin/eventos/professores")
    @ResponseBody
    public List<usuarioAdminDTO> listarProfessoresDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session) {

        if (!ehAdministrador(session)) {
            return List.of();
        }

        try {
            List<usuarioAdminDTO> professores = adminTurmaDAO.listarProfessoresDaTurma(turmaId);
            List<usuarioAdminDTO> professoresAtivos = new ArrayList<>();

            for (usuarioAdminDTO professor : professores) {
                if (professor.isStatus()) {
                    professoresAtivos.add(professor);
                }
            }

            return professoresAtivos;

        } catch (SQLException e) {
            logger.error("erro ao buscar professores da turma {}", turmaId, e);
            return List.of();
        }
    }

    // busca os conteudos da turma escolhida
    @GetMapping("/admin/eventos/conteudos")
    @ResponseBody
    public List<ConteudoDTO> listarConteudosDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session) {

        if (!ehAdministrador(session)) {
            return List.of();
        }

        try {
            return conteudoService.listarPorTurma(turmaId);

        } catch (RuntimeException e) {
            logger.error("erro ao buscar conteudos da turma {}", turmaId, e);
            return List.of();
        }
    }

    // cadastra o evento para a turma e professor selecionados
    @PostMapping("/admin/eventos/cadastrar")
    public String cadastrarEvento(
            @RequestParam("nome") String nome,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam("inicio") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inicio,
            @RequestParam("fim") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fim,
            @RequestParam(value = "prioridade", defaultValue = "MEDIA") String prioridade,
            @RequestParam("turmaId") Long turmaId,
            @RequestParam("idProfessor") Long idProfessor,
            @RequestParam(value = "conteudoIds", required = false) List<Long> conteudoIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        String voltarCadastro = "redirect:/admin/eventos/cadastrar";

        if (nome == null || nome.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o nome do evento.");
            return voltarCadastro;
        }

        if (!tipoValido(tipo)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione um tipo válido.");
            return voltarCadastro;
        }

        if (fim.isBefore(inicio)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "A data final não pode ser anterior à data inicial.");
            return voltarCadastro;
        }

        try {
            if (adminTurmaDAO.buscarTurmaPorId(turmaId) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return voltarCadastro;
            }

            if (!professorPertenceATurma(turmaId, idProfessor)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "O professor selecionado não está ativo ou não pertence à turma.");
                return voltarCadastro;
            }

            if (!conteudosPertencemATurma(turmaId, conteudoIds)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Um dos conteúdos selecionados não pertence à turma.");
                return voltarCadastro;
            }

            Evento evento = new Evento(nome.trim(), inicio, fim, null, prioridade);
            evento.setTipo(tipo);
            evento.setDescricao(descricao);
            evento.setIdTurma(turmaId);
            evento.setIdProfessor(idProfessor);

            eventoService.adicionarEvento(evento, conteudoIds);

            logger.info("evento cadastrado pelo administrador turmaId={} professorId={}", turmaId, idProfessor);

            redirectAttributes.addFlashAttribute("mensagemSucesso", "Evento cadastrado com sucesso!");
            return "redirect:/admin/eventos";

        } catch (SQLException | RuntimeException e) {
            logger.error("erro ao cadastrar evento pelo administrador", e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível cadastrar o evento.");
            return voltarCadastro;
        }
    }

    // mostra os dados do evento para edicao
    @GetMapping("/admin/eventos/{idEvento}/editar")
    public String exibirEdicaoEvento(@PathVariable("idEvento") Long idEvento,
                                     HttpSession session, Model model, RedirectAttributes redirectAttributes) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            Evento evento = eventoService.buscarEventoPorId(idEvento);

            if (evento == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Evento não encontrado.");
                return "redirect:/admin/eventos";
            }

            String sql = """
                    SELECT id_conteudo
                    FROM public."EVENTO_CONTEUDO"
                    WHERE id_evento = ?
                    """;

            // busca os conteudos que ja estavam selecionados
            List<Long> conteudoIdsSelecionados = jdbcTemplate.queryForList(sql, Long.class, idEvento);

            List<usuarioAdminDTO> professores = adminTurmaDAO.listarProfessoresDaTurma(evento.getIdTurma());
            List<usuarioAdminDTO> professoresDisponiveis = new ArrayList<>();

            for (usuarioAdminDTO professor : professores) {
                if (professor.isStatus() || professor.getIdPerfil().equals(evento.getIdProfessor())) {
                    professoresDisponiveis.add(professor);
                }
            }

            model.addAttribute("evento", evento);
            model.addAttribute("turmas", adminTurmaDAO.listarTurmas());
            model.addAttribute("professores", professoresDisponiveis);
            model.addAttribute("conteudos", conteudoService.listarPorTurma(evento.getIdTurma()));
            model.addAttribute("conteudoIdsSelecionados", conteudoIdsSelecionados);

            return "Usuarios/Admin/editarEventoAdmin";

        } catch (SQLException | RuntimeException e) {
            logger.error("erro ao carregar edicao do evento id={}", idEvento, e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível carregar o evento para edição.");
            return "redirect:/admin/eventos";
        }
    }

    // salva as alteracoes realizadas pelo administrador
    @PostMapping("/admin/eventos/{idEvento}/editar")
    public String editarEvento(
            @PathVariable("idEvento") Long idEvento,
            @RequestParam("nome") String nome,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam("inicio") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime inicio,
            @RequestParam("fim") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime fim,
            @RequestParam("prioridade") String prioridade,
            @RequestParam("turmaId") Long turmaId,
            @RequestParam("idProfessor") Long idProfessor,
            @RequestParam(value = "conteudoIds", required = false) List<Long> conteudoIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        String voltarEdicao = "redirect:/admin/eventos/" + idEvento + "/editar";

        if (nome == null || nome.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o nome do evento.");
            return voltarEdicao;
        }

        if (!tipoValido(tipo)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione um tipo válido.");
            return voltarEdicao;
        }

        if (!prioridadeValida(prioridade)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione uma prioridade válida.");
            return voltarEdicao;
        }

        if (fim.isBefore(inicio)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "A data final não pode ser anterior à data inicial.");
            return voltarEdicao;
        }

        try {
            if (eventoService.buscarEventoPorId(idEvento) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Evento não encontrado.");
                return "redirect:/admin/eventos";
            }

            if (adminTurmaDAO.buscarTurmaPorId(turmaId) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return voltarEdicao;
            }

            if (!professorPertenceATurma(turmaId, idProfessor)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "O professor selecionado não está ativo ou não pertence à turma.");
                return voltarEdicao;
            }

            if (!conteudosPertencemATurma(turmaId, conteudoIds)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Um dos conteúdos selecionados não pertence à turma.");
                return voltarEdicao;
            }

            Evento dadosAtualizados = new Evento(nome.trim(), inicio, fim, null, prioridade);
            dadosAtualizados.setTipo(tipo);
            dadosAtualizados.setDescricao(descricao);
            dadosAtualizados.setIdTurma(turmaId);
            dadosAtualizados.setIdProfessor(idProfessor);

            eventoService.atualizarEvento(idEvento, dadosAtualizados, conteudoIds);

            logger.info("evento atualizado pelo administrador id={}", idEvento);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Evento atualizado com sucesso!");

            return "redirect:/admin/eventos";

        } catch (SQLException | RuntimeException e) {
            logger.error("erro ao atualizar evento id={}", idEvento, e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível atualizar o evento.");
            return voltarEdicao;
        }
    }

    // exclui o evento selecionado pelo administrador
    @PostMapping("/admin/eventos/{idEvento}/excluir")
    public String excluirEvento(@PathVariable("idEvento") Long idEvento, HttpSession session,
                                RedirectAttributes redirectAttributes) {

        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        try {
            eventoService.excluirEvento(idEvento);

            logger.info("evento excluido pelo administrador id={}", idEvento);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Evento excluído com sucesso!");

        } catch (RuntimeException e) {
            logger.error("erro ao excluir evento id={}", idEvento, e);
            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível excluir o evento.");
        }

        return "redirect:/admin/eventos";
    }

    // verifica se o professor esta ativo e participa da turma
    private boolean professorPertenceATurma(Long turmaId, Long idProfessor) throws SQLException {

        List<usuarioAdminDTO> professores = adminTurmaDAO.listarProfessoresDaTurma(turmaId);

        for (usuarioAdminDTO professor : professores) {
            if (idProfessor.equals(professor.getIdPerfil()) && professor.isStatus()) {
                return true;
            }
        }

        return false;
    }

    // verifica se os conteudos escolhidos pertencem a turma
    private boolean conteudosPertencemATurma(Long turmaId, List<Long> conteudoIds) {

        if (conteudoIds == null || conteudoIds.isEmpty()) {
            return true;
        }

        List<ConteudoDTO> conteudosDaTurma = conteudoService.listarPorTurma(turmaId);

        for (Long idConteudo : conteudoIds) {

            boolean encontrado = false;

            for (ConteudoDTO conteudo : conteudosDaTurma) {
                if (idConteudo != null && idConteudo.equals(conteudo.getId())) {
                    encontrado = true;
                    break;
                }
            }

            if (!encontrado) {
                return false;
            }
        }

        return true;
    }

    private boolean tipoValido(String tipo) {
        return "PROVA".equals(tipo) || "TRABALHO".equals(tipo)
                || "APRESENTACAO".equals(tipo) || "OUTRO".equals(tipo);
    }

    private boolean prioridadeValida(String prioridade) {
        return "ALTA".equals(prioridade) || "MEDIA".equals(prioridade) || "BAIXA".equals(prioridade);
    }

    // verifica se a sessao pertence a um administrador
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
