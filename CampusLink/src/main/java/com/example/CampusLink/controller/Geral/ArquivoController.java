package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.dao.usuarioDAO;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/arquivos")
public class ArquivoController {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(ArquivoController.class);

    private final ArquivoService arquivoService;

    public ArquivoController(
            ArquivoService arquivoService) {

        this.arquivoService = arquivoService;
    }


    /*
     * PÁGINA DE MATERIAIS
     */
    @GetMapping
    public String pagina(
            Model model,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("Acesso aos arquivos recusado: usuário não autenticado.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "pagina", null,
                            "Acesso aos arquivos recusado: usuário não autenticado.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/login";
        }

        model.addAttribute(
                "arquivos",
                arquivoService.listarTodos()
        );

        return "Geral/arquivos";
    }


    /*
     * UPLOAD
     */
    @PostMapping("/upload")
    public String upload(
            @RequestParam("arquivo")
            MultipartFile arquivo,

            @RequestParam(
                    value = "idConteudo",
                    required = false
            )
            Long idConteudo,

            HttpSession session,

            RedirectAttributes redirectAttributes) {

        if (!"professor".equals(
                session.getAttribute("tipoUsuario"))) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Apenas professores podem enviar materiais."
            );

            logger.warn("Upload recusado: usuário não é professor. conteudoId={}", idConteudo);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "upload", null,
                            MessageFormatter.arrayFormat("Upload recusado: usuário não é professor. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/arquivos";
        }

        try {

            arquivoService.salvar(
                    arquivo,
                    idConteudo
            );

            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Arquivo enviado com sucesso."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    e.getMessage()
            );
        }

        return "redirect:/arquivos";
    }


    /*
     * DOWNLOAD
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {

            logger.warn("Download recusado: usuário não autenticado. arquivoId={}", id);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "download", null,
                            MessageFormatter.arrayFormat("Download recusado: usuário não autenticado. arquivoId={}", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return ResponseEntity
                    .status(401)
                    .build();
        }

        ArquivoDTO arquivo =
                arquivoService.buscarPorId(id);

        byte[] dados =
                arquivoService.download(id);

        String contentDisposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                arquivo.getNomeOriginal(),
                                StandardCharsets.UTF_8
                        )
                        .build()
                        .toString();

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(
                                arquivo.getMimeType()
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition
                )
                .contentLength(dados.length)
                .body(dados);
    }


    /*
     * EXCLUSÃO
     */
    @PostMapping("/{id}/excluir")
    public String excluir(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!"professor".equals(
                session.getAttribute("tipoUsuario"))) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Apenas professores podem excluir materiais."
            );

            logger.warn("Exclusão de arquivo recusada: usuário não é professor. arquivoId={}", id);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Exclusão de arquivo recusada: usuário não é professor. arquivoId={}", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/arquivos";
        }

        try {

            arquivoService.excluir(id);

            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    "Arquivo excluído com sucesso."
            );

        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    e.getMessage()
            );
        }

        return "redirect:/arquivos";
    }
}
