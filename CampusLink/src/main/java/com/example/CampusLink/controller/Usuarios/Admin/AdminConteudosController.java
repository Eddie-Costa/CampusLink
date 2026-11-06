// moderacao de materiais disponibiliza a analise dos conteudos denunciados

package com.example.CampusLink.controller.Usuarios.Admin;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dto.DenunciaDTO;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.service.ConteudoService;
import com.example.CampusLink.service.DenunciaService;
import com.example.CampusLink.service.RevisaoConteudoService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// controle da revisao administrativa esta classe reune denuncias e encaminha liberacao ou reprovacao
@Controller
public class AdminConteudosController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(AdminConteudosController.class);

    private static final String URL_LOGIN_ADMIN = "/admin/login";

    private final ConteudoService conteudoService;
    private final DenunciaService denunciaService;
    private final RevisaoConteudoService revisaoConteudoService;
    private final ArquivoService arquivoService;

    // preparar analise de conteudos este construtor recebe os servicos de materiais denuncias revisoes e arquivos
    public AdminConteudosController(
            ConteudoService conteudoService,
            DenunciaService denunciaService,
            RevisaoConteudoService revisaoConteudoService,
            ArquivoService arquivoService
    ) {
        this.conteudoService = conteudoService;
        this.denunciaService = denunciaService;
        this.revisaoConteudoService = revisaoConteudoService;
        this.arquivoService = arquivoService;
    }

    // listar conteudos denunciados este metodo reune materiais denuncias e anexos para analise administrativa
    @GetMapping("/admin/conteudos-denunciados")
    public String listarConteudos(HttpSession session, Model model) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // carregar conteudos denunciados busca materiais relatos e arquivos e mostra listas vazias se houver falha
        try {
            List<ConteudoDTO> conteudos = conteudoService.listarParaAnaliseAdmin();
            Map<Long, List<DenunciaDTO>> denunciasPorConteudo = new HashMap<>();
            Map<Long, ArquivoDTO> arquivosPorConteudo = new HashMap<>();

            for (ConteudoDTO conteudo : conteudos) {
                denunciasPorConteudo.put(
                        conteudo.getId(),
                        denunciaService.listarPorConteudo(conteudo.getId())
                );

                ArquivoDTO arquivo = arquivoService.buscarMaisRecentePorConteudo(conteudo.getId());

                // incluir arquivo para analise associa o material quando existe arquivo vinculado ao conteudo
                if (arquivo != null) {
                    arquivosPorConteudo.put(conteudo.getId(), arquivo);
                }
            }

            model.addAttribute("conteudos", conteudos);
            model.addAttribute("denunciasPorConteudo", denunciasPorConteudo);
            model.addAttribute("arquivosPorConteudo", arquivosPorConteudo);

        } catch (RuntimeException e) {
            logger.error("erro ao carregar conteúdos para análise", e);
            // registro de erros confere se esta habilitado ao listar conteudos denunciados
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar conteudos denunciados tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "listarConteudos", null,
                            "erro ao carregar conteúdos para análise", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            model.addAttribute("mensagemErro", "Não foi possível carregar os conteúdos para análise.");
            model.addAttribute("conteudos", new ArrayList<ConteudoDTO>());
            model.addAttribute("denunciasPorConteudo", new HashMap<Long, List<DenunciaDTO>>());
            model.addAttribute("arquivosPorConteudo", new HashMap<Long, ArquivoDTO>());
        }

        return "Usuarios/Admin/conteudosDenunciados";
    }

    // liberar conteudo pela administracao este metodo solicita a reativacao do material em analise
    @PostMapping("/admin/conteudos-denunciados/{idConteudo}/liberar")
    public String liberarConteudo(
            @PathVariable Long idConteudo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // liberar conteudo analisado tenta aprovar o material e informa falhas da operacao
        try {
            boolean liberado = revisaoConteudoService.liberarConteudo(idConteudo);

            // confirmar liberacao distingue conteudo liberado de material que nao aguarda analise
            if (liberado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo liberado novamente.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não está aguardando análise.");
            }

        } catch (RuntimeException e) {
            logger.error("erro ao liberar o conteúdo {}", idConteudo, e);
            // registro de erros confere se esta habilitado ao liberar conteudo pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao liberar conteudo pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "liberarConteudo", null,
                            MessageFormatter.arrayFormat("erro ao liberar o conteúdo {}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível liberar o conteúdo.");
        }

        return "redirect:/admin/conteudos-denunciados";
    }

    // reprovar conteudo pela administracao este metodo exige um motivo e solicita a remocao do material
    @PostMapping("/admin/conteudos-denunciados/{idConteudo}/reprovar")
    public String reprovarConteudo(
            @PathVariable Long idConteudo,
            @RequestParam String comentarioAdmin,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        // exigir login administrativo encaminha sessoes sem usuario para o acesso do administrador
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:" + URL_LOGIN_ADMIN;
        }

        // exigir administrador retorna ao inicio quando a sessao nao comprova o acesso administrativo
        if (!ehAdministrador(session)) {
            return "redirect:/home";
        }

        // exigir motivo da reprovacao recusa a remocao quando o comentario do administrador esta vazio
        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            logger.warn("Reprovação recusada: motivo vazio. conteudoId={}", idConteudo);
            // registro de avisos confere se esta habilitado ao reprovar conteudo pela administracao
            if (logger.isWarnEnabled()) {
                // gravar historico ao reprovar conteudo pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", AdminConteudosController.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("Reprovação recusada: motivo vazio. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute("mensagemErro", "Informe o motivo da reprovação.");

            return "redirect:/admin/conteudos-denunciados";
        }

        // reprovar conteudo analisado tenta remover o material e informa falhas da operacao
        try {
            boolean reprovado = revisaoConteudoService.reprovarConteudo(idConteudo, comentarioAdmin);

            // confirmar reprovacao distingue material removido de conteudo que nao aguarda analise
            if (reprovado) {
                redirectAttributes.addFlashAttribute("mensagemSucesso", "Conteúdo reprovado e removido.");
            } else {
                redirectAttributes.addFlashAttribute("mensagemErro", "Este conteúdo não está aguardando análise.");
            }

        } catch (RuntimeException e) {
            logger.error("erro ao reprovar o conteúdo {}", idConteudo, e);
            // registro de erros confere se esta habilitado ao reprovar conteudo pela administracao
            if (logger.isErrorEnabled()) {
                // gravar historico ao reprovar conteudo pela administracao tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", AdminConteudosController.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("erro ao reprovar o conteúdo {}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            redirectAttributes.addFlashAttribute("mensagemErro", "Não foi possível reprovar o conteúdo.");
        }

        return "redirect:/admin/conteudos-denunciados";
    }

    // validar administrador da analise este metodo compara o perfil e o email com a conta conectada
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
