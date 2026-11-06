// publicacoes do professor organiza a consulta e as acoes dos seus materiais
package com.example.CampusLink.controller.Usuarios.Professor;

import com.example.CampusLink.repository.ProfessorRepository;
import com.example.CampusLink.repository.TurmaRepository;
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

// controle de meus conteudos esta classe aplica filtros e permite remover reabrir e pedir revisao
@Controller
public class MeusConteudosController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    private static final Logger logger = LoggerFactory.getLogger(MeusConteudosController.class);

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final ProfessorRepository professorRepository;

    // preparar meus conteudos este construtor recebe as consultas de materiais denuncias e professor
    public MeusConteudosController(ConteudoService conteudoService, DenunciaService denunciaService, ProfessorRepository professorRepository) {
        this.conteudoService = conteudoService;
        this.denunciaService = denunciaService;
        this.professorRepository = professorRepository;
    }

    // listar meus conteudos este metodo filtra e pagina os materiais do professor com denuncias e totais
    @GetMapping("/meus-conteudos")
    public String meusConteudos(
            HttpSession session,
            Model model,
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @RequestParam(value = "busca", defaultValue = "") String busca,
            @RequestParam(value = "status", defaultValue = "todos") String status,
            @RequestParam(value = "fragmento", defaultValue = "false") boolean fragmento
    ) {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir area do professor retorna ao inicio quando a sessao pertence a outro perfil
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        // exigir email de acesso retorna ao login quando o email esta ausente ou vazio
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        pagina = Math.max(0, pagina);
        busca = busca == null ? "" : busca.trim();
        status = status == null ? "todos" : status.trim().toLowerCase(Locale.ROOT);

        // corrigir filtro pessoal usa todas as situacoes quando o valor recebido nao e reconhecido
        if (!"ativo".equals(status) && !"suspenso_denuncia".equals(status) && !"removido".equals(status)) {
            status = "todos";
        }

        // carregar meus conteudos busca materiais denuncias prazos e totais e trata falhas de consulta
        try {
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
            // exigir professor cadastrado retorna ao inicio quando nao encontra o codigo do professor
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            List<ConteudoDTO> conteudos = conteudoService.listarPorProfessorPaginado(idProfessor, pagina, busca, status);
            int totalFiltrados = conteudoService.contarFiltradosPorProfessor(idProfessor, busca, status);


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

            // devolver lista pessoal retorna apenas a area de conteudos para pedidos de carregamento parcial
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

            // registro de erros confere se esta habilitado ao listar meus conteudos
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar meus conteudos tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", MeusConteudosController.class.getName(), "meusConteudos", null,
                            "Erro ao carregar conteúdos do professor.", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            // informar falha parcial retorna erro ao navegador quando a busca de mais conteudos falha
            if (fragmento) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível carregar os conteúdos.", e);
            }

            return "redirect:/home";
        }
    }

    // montar midias dos meus conteudos este metodo identifica miniaturas e tipos de livros videos textos e arquivos
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

            // analisar link preenchido procura a midia apenas quando o conteudo possui endereco
            if (url != null && !url.isBlank()) {
                // interpretar endereco pessoal extrai os dados do link e registra formatos invalidos
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    // reconhecer dominio de midia verifica o provedor apenas quando o endereco informa um servidor
                    if (host != null) {
                        host = host.toLowerCase(Locale.ROOT);
                        // identificar video do professor procura o codigo quando o dominio pertence ao youtube
                        if (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be")) {
                            String videoId = extrairIdVideo(uri);
                            // incluir video pessoal guarda miniatura codigo e tipo quando encontra um video valido
                            if (videoId != null) {
                                miniaturasPorConteudo.put(conteudo.getId(),
                                        "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg");
                                tiposMidiaPorConteudo.put(conteudo.getId(), "video");
                                videoIdsPorConteudo.put(conteudo.getId(), videoId);
                                tipoExibicao = "Vídeo";
                            }
                        }
                        // identificar livro do professor procura o codigo nos enderecos do catalogo de livros
                        else if (host.equals("books.google.com") || host.equals("play.google.com")) {
                            String livroId = extrairParametro(uri.getRawQuery(), "id");
                            // procurar livro sem parametro tenta obter o codigo pelo caminho do endereco
                            if (livroId == null && host.equals("books.google.com")) {
                                String path = uri.getPath();
                                String[] partes = path == null ? new String[0] : path.split("/");
                                // ler ultimo trecho do livro continua a busca quando o caminho possui partes
                                if (partes.length > 0) {
                                    String ultimoSegmento = partes[partes.length - 1];
                                    // aceitar codigo no caminho usa o trecho final quando o formato do codigo e valido
                                    if (ultimoSegmento.matches("[A-Za-z0-9_-]{1,128}")) {
                                        livroId = ultimoSegmento;
                                    }
                                }
                            }
                            // incluir livro pessoal guarda capa codigo e tipo quando o identificador e valido
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

    // identificar video dos meus conteudos este metodo extrai o codigo valido do endereco informado
    private String extrairIdVideo(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String videoId = null;

        // interpretar video curto extrai o codigo do caminho quando o link usa o dominio curto
        if (host.equals("youtu.be")) {
            String path = uri.getPath();
            // ler codigo do caminho usa o primeiro trecho depois da barra quando ele existe
            if (path != null && path.length() > 1) {
                videoId = path.substring(1).split("/")[0];
            }
        } else {
            videoId = extrairParametro(uri.getRawQuery(), "v");
            // procurar formato alternativo busca no caminho quando o parametro do video esta ausente
            if (videoId == null) {
                String[] partes = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
                for (int i = 0; i < partes.length - 1; i++) {
                    // localizar video incorporado aceita o codigo apos os caminhos de incorporacao video curto ou transmissao
                    if ("embed".equals(partes[i]) || "shorts".equals(partes[i]) || "live".equals(partes[i])) {
                        videoId = partes[i + 1];
                        break;
                    }
                }
            }
        }

        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}") ? videoId : null;
    }

    // ler parametro dos meus conteudos este metodo busca e decodifica um valor do endereco
    private String extrairParametro(String query, String nome) {
        // ignorar consulta vazia retorna sem valor quando faltam parametros no endereco
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String parametro : query.split("&")) {
            String[] par = parametro.split("=", 2);
            // encontrar parametro pessoal devolve o valor decodificado quando o nome do parametro corresponde
            if (par.length == 2 && nome.equals(URLDecoder.decode(par[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(par[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // delete conteudo do professor pela tela este metodo identifica o autor e solicita a remocao do material
    @PostMapping("/meus-conteudos/{idConteudo}/remover")
    public String removerConteudo(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir area do professor retorna ao inicio quando a sessao pertence a outro perfil
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        // exigir email de acesso retorna ao login quando o email esta ausente ou vazio
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        // delete conteudo pessoal identifica o professor tenta remover o material e trata falhas
        try {
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
            // exigir professor cadastrado retorna ao inicio quando nao encontra o codigo do professor
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            boolean removido = conteudoService.removerConteudoProfessor(idConteudo, idProfessor);

            // confirmar remocao pessoal mostra sucesso ou informa que o conteudo nao foi removido
            if (removido) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo removido com sucesso.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível remover o conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao identificar professor para remoção. conteudoId={}", idConteudo, e);

            // registro de erros confere se esta habilitado ao delete conteudo do professor pela tela
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete conteudo do professor pela tela tenta salvar o registro no banco e informa falhas
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

    // solicitar revisao pela tela este metodo identifica o professor e encaminha o material para analise
    @PostMapping("/meus-conteudos/{idConteudo}/solicitar-revisao")
    public String solicitarRevisao(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir area do professor retorna ao inicio quando a sessao pertence a outro perfil
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        // exigir email de acesso retorna ao login quando o email esta ausente ou vazio
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        // solicitar revisao pessoal identifica o professor envia o pedido e trata falhas
        try {
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
            // exigir professor cadastrado retorna ao inicio quando nao encontra o codigo do professor
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            Long idProfessor = Long.parseLong(idProfessorTexto);
            boolean solicitado = conteudoService.solicitarRevisao(idConteudo, idProfessor);

            // confirmar pedido de revisao informa se a solicitacao foi enviada ao administrador
            if (solicitado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Solicitação de análise enviada ao administrador.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível solicitar a análise deste conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao identificar professor para revisão. conteudoId={}", idConteudo, e);

            // registro de erros confere se esta habilitado ao solicitar revisao pela tela
            if (logger.isErrorEnabled()) {
                // gravar historico ao solicitar revisao pela tela tenta salvar o registro no banco e informa falhas
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

    // reabrir material do professor este metodo disponibiliza o conteudo e registra a alteracao nas turmas
    @PostMapping("/meus-conteudos/{idConteudo}/re-disponibilizar")
    public String reDisponibilizar(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        // exigir usuario conectado retorna ao login quando a sessao nao possui usuario
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir area do professor retorna ao inicio quando a sessao pertence a outro perfil
        if (!"professor".equals(session.getAttribute("tipoUsuario"))) {
            return "redirect:/home";
        }

        String email = (String) session.getAttribute("email2FA");
        // exigir email de acesso retorna ao login quando o email esta ausente ou vazio
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        // reabrir conteudo pessoal identifica o professor e trata falhas da nova disponibilizacao
        try {
            String idProfessorTexto = professorRepository.buscarPorIDProfessor(email);
            // exigir professor cadastrado retorna ao inicio quando nao encontra o codigo do professor
            if (idProfessorTexto == null || idProfessorTexto.isBlank()) {
                return "redirect:/home";
            }

            // confirmar nova disponibilizacao registra a mudanca quando o conteudo foi reaberto
            if (conteudoService.reabrir(idConteudo, Long.parseLong(idProfessorTexto), null)) {
                turmaRepository.registrarAlteracaoPorConteudo(email, idConteudo);
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo disponível novamente para os alunos concluírem.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível re-disponibilizar este conteúdo.");
            }

        } catch (SQLException | RuntimeException e) {
            logger.error("Erro ao re-disponibilizar conteúdo. conteudoId={}", idConteudo, e);

            // registro de erros confere se esta habilitado ao reabrir material do professor
            if (logger.isErrorEnabled()) {
                // gravar historico ao reabrir material do professor tenta salvar o registro no banco e informa falhas
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