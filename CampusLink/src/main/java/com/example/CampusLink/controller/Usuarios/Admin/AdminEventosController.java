// agenda administrativa disponibiliza o gerenciamento dos eventos da plataforma

package com.example.CampusLink.controller.Usuarios.Admin;

import com.example.CampusLink.repository.AdminTurmaRepository;
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

// controle administrativo de eventos esta classe valida turmas professores e materiais nos formularios
@Controller
public class AdminEventosController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEventosController.class);

    private final EventoService eventoService;
    private final AdminTurmaRepository adminTurmaRepository;
    private final ConteudoService conteudoService;
    private final JdbcTemplate jdbcTemplate;

    // preparar controle administrativo de eventos este construtor recebe os servicos de eventos turmas e conteudos
    public AdminEventosController(EventoService eventoService, AdminTurmaRepository adminTurmaRepository,
                                  ConteudoService conteudoService, JdbcTemplate jdbcTemplate) {
        this.eventoService = eventoService;
        this.adminTurmaRepository = adminTurmaRepository;
        this.conteudoService = conteudoService;
        this.jdbcTemplate = jdbcTemplate;
    }


    // listar eventos administrativos este metodo carrega os eventos para a tela de administracao
    @GetMapping("/admin/eventos")
    public String listarEventos(HttpSession session, Model model) {

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // listar eventos administrativos busca os compromissos e apresenta uma lista vazia se a consulta falhar
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


    // abrir cadastro administrativo de evento este metodo carrega as turmas disponiveis no formulario
    @GetMapping("/admin/eventos/cadastrar")
    public String exibirCadastroEvento(HttpSession session, Model model) {

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // preparar turmas do evento consulta as opcoes e mostra lista vazia quando o banco falha
        try {
            model.addAttribute("turmas", adminTurmaRepository.listarTurmas());

        } catch (SQLException e) {
            logger.error("erro ao carregar turmas para cadastro de evento", e);
            model.addAttribute("turmas", new ArrayList<>());
            model.addAttribute("mensagemErro", "Não foi possível carregar as turmas.");
        }

        return "Usuarios/Admin/cadastrarEventoAdmin";
    }


    // listar professores para evento administrativo este metodo retorna apenas professores ativos da turma
    @GetMapping("/admin/eventos/professores")
    @ResponseBody
    public List<usuarioAdminDTO> listarProfessoresDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session) {

        // proteger professores da turma retorna lista vazia para acesso sem permissao administrativa
        if (!ehAdministrador(session)) {
            return List.of();
        }

        // consultar professores ativos busca os vinculos da turma e retorna vazio se a consulta falhar
        try {
            List<usuarioAdminDTO> professores = adminTurmaRepository.listarProfessoresDaTurma(turmaId);
            List<usuarioAdminDTO> professoresAtivos = new ArrayList<>();

            for (usuarioAdminDTO professor : professores) {
                // incluir professor ativo aceita somente contas ativas na selecao de responsavel
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


    // listar conteudos para evento administrativo este metodo carrega os materiais da turma escolhida
    @GetMapping("/admin/eventos/conteudos")
    @ResponseBody
    public List<ConteudoDTO> listarConteudosDaTurma(@RequestParam("turmaId") Long turmaId, HttpSession session) {

        // proteger conteudos da turma retorna lista vazia para acesso sem permissao administrativa
        if (!ehAdministrador(session)) {
            return List.of();
        }

        // consultar materiais da turma devolve os conteudos ou lista vazia se a busca falhar
        try {
            return conteudoService.listarPorTurma(turmaId);

        } catch (RuntimeException e) {
            logger.error("erro ao buscar conteudos da turma {}", turmaId, e);
            return List.of();
        }
    }


    // cadastrar evento pela administracao este metodo valida os dados e vinculos antes de gravar
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

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        String voltarCadastro = "redirect:/admin/eventos/cadastrar";

        // exigir nome no cadastro administrativo recusa um evento sem nome preenchido
        if (nome == null || nome.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o nome do evento.");
            return voltarCadastro;
        }

        // validar tipo do novo evento recusa valores fora das opcoes permitidas
        if (!tipoValido(tipo)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione um tipo válido.");
            return voltarCadastro;
        }

        String erroHorario = EventoService.validarHorario(inicio, fim);
        // validar horario do novo evento retorna ao cadastro com o motivo do intervalo invalido
        if (erroHorario != null) {
            redirectAttributes.addFlashAttribute("mensagemErro", erroHorario);
            return voltarCadastro;
        }

        // cadastrar evento administrativo confere vinculos salva o evento e trata falhas de consulta ou gravacao
        try {
            // exigir turma existente impede cadastrar evento em uma turma nao encontrada
            if (adminTurmaRepository.buscarTurmaPorId(turmaId) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return voltarCadastro;
            }

            // conferir professor do novo evento exige professor ativo vinculado a turma escolhida
            if (!professorPertenceATurma(turmaId, idProfessor)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "O professor selecionado não está ativo ou não pertence à turma.");
                return voltarCadastro;
            }

            // conferir materiais do novo evento recusa conteudos que nao pertencem a turma
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


    // abrir edicao administrativa de evento este metodo carrega o evento e suas selecoes atuais
    @GetMapping("/admin/eventos/{idEvento}/editar")
    public String exibirEdicaoEvento(@PathVariable("idEvento") Long idEvento,
                                     HttpSession session, Model model, RedirectAttributes redirectAttributes) {

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // preparar edicao do evento carrega dados e vinculos e retorna a lista quando a consulta falha
        try {
            Evento evento = eventoService.buscarEventoPorId(idEvento);

            // localizar evento da edicao avisa quando o compromisso nao existe mais
            if (evento == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Evento não encontrado.");
                return "redirect:/admin/eventos";
            }

            String sql = """
                    SELECT id_conteudo
                    FROM public."EVENTO_CONTEUDO"
                    WHERE id_evento = ?
                    """;


            List<Long> conteudoIdsSelecionados = jdbcTemplate.queryForList(sql, Long.class, idEvento);

            List<usuarioAdminDTO> professores = adminTurmaRepository.listarProfessoresDaTurma(evento.getIdTurma());
            List<usuarioAdminDTO> professoresDisponiveis = new ArrayList<>();

            for (usuarioAdminDTO professor : professores) {
                // preservar responsavel atual inclui professores ativos e tambem o responsavel ja salvo no evento
                if (professor.isStatus() || professor.getIdPerfil().equals(evento.getIdProfessor())) {
                    professoresDisponiveis.add(professor);
                }
            }

            model.addAttribute("evento", evento);
            model.addAttribute("turmas", adminTurmaRepository.listarTurmas());
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


    // editar evento pela administracao este metodo confere os dados e atualiza o evento com seus conteudos
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

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        String voltarEdicao = "redirect:/admin/eventos/" + idEvento + "/editar";

        // exigir nome na edicao administrativa recusa o formulario quando o nome esta vazio
        if (nome == null || nome.isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o nome do evento.");
            return voltarEdicao;
        }

        // validar tipo na edicao recusa valores fora das opcoes permitidas
        if (!tipoValido(tipo)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione um tipo válido.");
            return voltarEdicao;
        }

        // validar prioridade na edicao recusa niveis fora das opcoes permitidas
        if (!prioridadeValida(prioridade)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Selecione uma prioridade válida.");
            return voltarEdicao;
        }

        String erroHorario = EventoService.validarHorario(inicio, fim);
        // validar horario na edicao retorna ao formulario com o motivo do intervalo invalido
        if (erroHorario != null) {
            redirectAttributes.addFlashAttribute("mensagemErro", erroHorario);
            return voltarEdicao;
        }

        // salvar edicao administrativa confere evento e vinculos e informa falhas de consulta ou gravacao
        try {
            // exigir evento existente impede atualizar um compromisso que nao foi encontrado
            if (eventoService.buscarEventoPorId(idEvento) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Evento não encontrado.");
                return "redirect:/admin/eventos";
            }

            // exigir turma na edicao impede salvar uma turma que nao existe
            if (adminTurmaRepository.buscarTurmaPorId(turmaId) == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Turma não encontrada.");
                return voltarEdicao;
            }

            // conferir professor na edicao exige professor ativo vinculado a turma escolhida
            if (!professorPertenceATurma(turmaId, idProfessor)) {
                redirectAttributes.addFlashAttribute("mensagemErro", "O professor selecionado não está ativo ou não pertence à turma.");
                return voltarEdicao;
            }

            // conferir materiais na edicao recusa conteudos que nao pertencem a turma
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


    // delete evento pela administracao este metodo solicita a exclusao e informa o resultado na tela
    @PostMapping("/admin/eventos/{idEvento}/excluir")
    public String excluirEvento(@PathVariable("idEvento") Long idEvento, HttpSession session,
                                RedirectAttributes redirectAttributes) {

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // delete evento administrativo tenta excluir o compromisso e mostra o resultado ou a falha
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


    // verificar professor do evento administrativo este metodo exige um professor ativo vinculado a turma
    private boolean professorPertenceATurma(Long turmaId, Long idProfessor) throws SQLException {

        List<usuarioAdminDTO> professores = adminTurmaRepository.listarProfessoresDaTurma(turmaId);

        for (usuarioAdminDTO professor : professores) {
            // reconhecer professor vinculado confirma o codigo do professor e sua conta ativa
            if (idProfessor.equals(professor.getIdPerfil()) && professor.isStatus()) {
                return true;
            }
        }

        return false;
    }


    // verificar conteudos do evento administrativo este metodo confere se todos pertencem a turma escolhida
    private boolean conteudosPertencemATurma(Long turmaId, List<Long> conteudoIds) {

        // aceitar evento sem materiais considera valida uma selecao de conteudos vazia
        if (conteudoIds == null || conteudoIds.isEmpty()) {
            return true;
        }

        List<ConteudoDTO> conteudosDaTurma = conteudoService.listarPorTurma(turmaId);

        for (Long idConteudo : conteudoIds) {

            boolean encontrado = false;

            for (ConteudoDTO conteudo : conteudosDaTurma) {
                // localizar material da turma confirma o vinculo quando encontra o codigo selecionado
                if (idConteudo != null && idConteudo.equals(conteudo.getId())) {
                    encontrado = true;
                    break;
                }
            }

            // recusar material nao encontrado interrompe a validacao se um conteudo nao pertence a turma
            if (!encontrado) {
                return false;
            }
        }

        return true;
    }

    // validar tipo de evento este metodo aceita prova trabalho apresentacao ou outro
    private boolean tipoValido(String tipo) {
        return "PROVA".equals(tipo) || "TRABALHO".equals(tipo)
                || "APRESENTACAO".equals(tipo) || "OUTRO".equals(tipo);
    }

    // validar prioridade de evento este metodo aceita prioridade alta media ou baixa
    private boolean prioridadeValida(String prioridade) {
        return "ALTA".equals(prioridade) || "MEDIA".equals(prioridade) || "BAIXA".equals(prioridade);
    }


    // validar administrador dos eventos este metodo confere o perfil e o email da conta conectada
    private boolean ehAdministrador(HttpSession session) {

        Object tipoUsuario = session.getAttribute("tipoUsuario");
        Object usuarioLogado = session.getAttribute("usuarioLogado");
        Object emailSessao = session.getAttribute("email2FA");

        // conferir perfil administrativo rejeita sessoes com outro tipo de usuario
        if (!"admin".equals(tipoUsuario)) {
            return false;
        }

        // conferir dados administrativos rejeita um usuario que nao possui os dados de administrador
        if (!(usuarioLogado instanceof loginAdminDTO)) {
            return false;
        }

        // conferir email da sessao rejeita valores que nao sao texto
        if (!(emailSessao instanceof String)) {
            return false;
        }

        loginAdminDTO admin = (loginAdminDTO) usuarioLogado;

        // exigir email do administrador rejeita contas sem email preenchido
        if (admin.getEmail() == null || admin.getEmail().isBlank()) {
            return false;
        }

        return admin.getEmail().trim().equalsIgnoreCase(((String) emailSessao).trim());
    }
}
