package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.dao.professorDAO;
import com.example.CampusLink.dao.usuarioDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DenunciaService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class MeusConteudosController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(MeusConteudosController.class);

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final professorDAO professorDAO;

    public MeusConteudosController(ConteudoService conteudoService, DenunciaService denunciaService, professorDAO professorDAO) {
        this.conteudoService = conteudoService;
        this.denunciaService = denunciaService;
        this.professorDAO = professorDAO;
    }

    @GetMapping("/meus-conteudos")
    public String meusConteudos(
            HttpSession session,
            Model model,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "busca", defaultValue = "") String busca,
            @RequestParam(value = "status", defaultValue = "todos") String status,
            @RequestParam(value = "fragmento", defaultValue = "false") boolean fragmento
    ) {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        pagina = Math.max(0, pagina);
        busca = busca == null ? "" : busca.trim();
        status = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        if (!"ativo".equals(status) && !"suspenso_denuncia".equals(status) && !"removido".equals(status)) {
            status = "todos";
        }

        try {
            String idProfessorTexto = professorDAO.buscarPorIDProfessor(email);
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            List<ConteudoDTO> conteudos = conteudoService.listarPorProfessorPaginado(idProfessor, pagina, busca, status);
            int totalFiltrados = conteudoService.contarFiltradosPorProfessor(idProfessor, busca, status);

            // busca as denuncias somente dos conteudos deste lote
            for (ConteudoDTO conteudo : conteudos) {
                List<String> alunosDenunciaram = denunciaService.listarNomesAlunos(conteudo.getId());
                conteudo.setAlunosDenunciaram(alunosDenunciaram);
            }

            model.addAttribute("conteudos", conteudos);
            model.addAttribute("busca", busca);
            model.addAttribute("statusSelecionado", status);
            model.addAttribute("totalFiltrados", totalFiltrados);
            model.addAttribute("proximaPagina", (long) pagina + 1);
            model.addAttribute("temMais", (long) pagina * 10 + conteudos.size() < totalFiltrados);

            if (fragmento) {
                return "Usuarios/Professor/meusConteudos :: blocoConteudos";
            }

            Map<String, Integer> totais = conteudoService.contarPorProfessor(idProfessor);
            model.addAttribute("totalConteudos", totais.get("totalConteudos"));
            model.addAttribute("totalAtivos", totais.get("totalAtivos"));
            model.addAttribute("totalSuspensos", totais.get("totalSuspensos"));
            model.addAttribute("totalRemovidos", totais.get("totalRemovidos"));

            return "Usuarios/Professor/meusConteudos";

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao carregar conteúdos do professor.", e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "meusConteudos", null,
                            "Erro ao carregar conteúdos do professor.", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            if (fragmento) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível carregar os conteúdos.", e);
            }

            return "redirect:/home";
        }
    }

    @PostMapping("/meus-conteudos/{idConteudo}/remover")
    public String removerConteudo(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        try {
            String idProfessorTexto = professorDAO.buscarPorIDProfessor(email);
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            boolean removido = conteudoService.removerConteudoProfessor(idConteudo, idProfessor);

            if (removido) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo removido com sucesso.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao identificar professor para remoção. conteudoId={}", idConteudo, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "removerConteudo", null,
                            MessageFormatter.arrayFormat("Erro ao identificar professor para remoção. conteudoId={}", new Object[]{idConteudo}).getMessage(),
                            null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o conteúdo.");
        }

        return "redirect:/meus-conteudos";
    }

    @PostMapping("/meus-conteudos/{idConteudo}/solicitar-revisao")
    public String solicitarRevisao(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        try {
            String idProfessorTexto = professorDAO.buscarPorIDProfessor(email);
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            boolean solicitado = conteudoService.solicitarRevisao(idConteudo, idProfessor);

            if (solicitado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Solicitação de análise enviada ao administrador.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível solicitar a análise deste conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao identificar professor para revisão. conteudoId={}", idConteudo, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "solicitarRevisao", null,
                            MessageFormatter.arrayFormat("Erro ao identificar professor para revisão. conteudoId={}", new Object[]{idConteudo}).getMessage(),
                            null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível solicitar a análise.");
        }

        return "redirect:/meus-conteudos";
    }
}