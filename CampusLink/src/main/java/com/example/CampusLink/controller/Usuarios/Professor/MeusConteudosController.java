package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.UsuarioRepository;
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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class MeusConteudosController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(MeusConteudosController.class);

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final ProfessorRepository professorRepository;

    public MeusConteudosController(ConteudoService conteudoService, DenunciaService denunciaService, ProfessorRepository professorRepository) {
        this.conteudoService = conteudoService;
        this.denunciaService = denunciaService;
        this.professorRepository = professorRepository;
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
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
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
            model.addAttribute("prazosEncerrados",
                    conteudoService.listarPrazosEncerrados(conteudos.stream().map(ConteudoDTO::getId).toList()));
            adicionarInformacoesMidia(model, conteudos);
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
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "meusConteudos", null,
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

    private void adicionarInformacoesMidia(Model model, List<ConteudoDTO> conteudos) {
        Map<Long, String> miniaturasPorConteudo = new HashMap<>();
        Map<Long, String> tiposMidiaPorConteudo = new HashMap<>();
        Map<Long, String> videoIdsPorConteudo = new HashMap<>();
        Map<Long, String> livroIdsPorConteudo = new HashMap<>();
        Map<Long, String> tiposExibicaoPorConteudo = new HashMap<>();

        for (ConteudoDTO conteudo : conteudos) {
            String tipo = conteudo.getTipo() == null ? "" : conteudo.getTipo().toLowerCase(Locale.ROOT);
            String tipoExibicao = switch (tipo) {
                case "arquivo" -> "Arquivo";
                case "texto" -> "Texto";
                default -> "Link";
            };
            String url = conteudo.getUrl();

            if (url != null && !url.isBlank()) {
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    if (host != null) {
                        host = host.toLowerCase(Locale.ROOT);
                        if (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be")) {
                            String videoId = extrairIdVideo(uri);
                            if (videoId != null) {
                                miniaturasPorConteudo.put(conteudo.getId(),
                                        "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
                                tiposMidiaPorConteudo.put(conteudo.getId(), "video");
                                videoIdsPorConteudo.put(conteudo.getId(), videoId);
                                tipoExibicao = "Vídeo";
                            }
                        } else if (host.equals("books.google.com") || host.equals("play.google.com")) {
                            String livroId = extrairParametro(uri.getRawQuery(), "id");
                            if (livroId == null && host.equals("books.google.com")) {
                                String path = uri.getPath();
                                String[] partes = path == null ? new String[0] : path.split("/");
                                if (partes.length > 0) {
                                    String ultimoSegmento = partes[partes.length - 1];
                                    if (ultimoSegmento.matches("[A-Za-z0-9_-]{1,128}")) {
                                        livroId = ultimoSegmento;
                                    }
                                }
                            }
                            if (livroId != null && livroId.matches("[A-Za-z0-9_-]{1,128}")) {
                                miniaturasPorConteudo.put(conteudo.getId(),
                                        "https://books.google.com/books/content?id="
                                                + java.net.URLEncoder.encode(livroId, StandardCharsets.UTF_8)
                                                + "&printsec=frontcover&img=1&zoom=1");
                                tiposMidiaPorConteudo.put(conteudo.getId(), "livro");
                                livroIdsPorConteudo.put(conteudo.getId(), livroId);
                                tipoExibicao = "Livro";
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    logger.warn("URL inválida ao identificar a mídia do conteúdo {}", conteudo.getId());
                }
            }

            tiposExibicaoPorConteudo.put(conteudo.getId(), tipoExibicao);
        }

        model.addAttribute("miniaturasPorConteudo", miniaturasPorConteudo);
        model.addAttribute("tiposMidiaPorConteudo", tiposMidiaPorConteudo);
        model.addAttribute("videoIdsPorConteudo", videoIdsPorConteudo);
        model.addAttribute("livroIdsPorConteudo", livroIdsPorConteudo);
        model.addAttribute("tiposExibicaoPorConteudo", tiposExibicaoPorConteudo);
    }

    private String extrairIdVideo(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String videoId = null;

        if (host.equals("youtu.be")) {
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                videoId = path.substring(1).split("/")[0];
            }
        } else {
            videoId = extrairParametro(uri.getRawQuery(), "v");
            if (videoId == null) {
                String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                for (int i = 0; i < partes.length - 1; i++) {
                    if ("embed".equals(partes[i]) || "shorts".equals(partes[i]) || "live".equals(partes[i])) {
                        videoId = partes[i + 1];
                        break;
                    }
                }
            }
        }

        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    private String extrairParametro(String query, String nome) {
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            if (par.length == 2 && nome.equals(URLDecoder.decode(par[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(par[1], StandardCharsets.UTF_8);
            }
        }
        return null;
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
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
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
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "removerConteudo", null,
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
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
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
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "solicitarRevisao", null,
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

    @PostMapping("/meus-conteudos/{idConteudo}/re-disponibilizar")
    public String reDisponibilizar(
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
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            if (conteudoService.reabrir(idConteudo, Long.parseLong(idProfessorTexto))) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo disponível novamente para os alunos concluírem.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível re-disponibilizar este conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao re-disponibilizar conteúdo. conteudoId={}", idConteudo, e);

            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "reDisponibilizar", null,
                            MessageFormatter.arrayFormat("Erro ao re-disponibilizar conteúdo. conteudoId={}", new Object[]{idConteudo}).getMessage(),
                            null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível re-disponibilizar o conteúdo.");
        }

        return "redirect:/meus-conteudos";
    }
}