package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.service.ArquivoService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@Controller
@RequestMapping("/arquivos")
public class ArquivoController {

    private final ArquivoService arquivoService;
    private final conteudoDAO conteudoDAO;

    public ArquivoController(
            ArquivoService arquivoService,
            conteudoDAO conteudoDAO) {

        this.arquivoService = arquivoService;
        this.conteudoDAO = conteudoDAO;
    }


    /*
     * pagina de materiais
     */
    @GetMapping
    public String pagina(
            Model model,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        model.addAttribute(
                "arquivos",
                arquivoService.listarTodos()
        );

        return "Geral/arquivos";
    }


    /*
     * upload
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
     * download
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            HttpSession session) {

        if (session.getAttribute("usuarioLogado") == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        ArquivoDTO arquivo =
                arquivoService.buscarPorId(id);

        if (arquivo.getIdConteudo() != null) {

            try {

                String statusConteudo =
                        conteudoDAO.buscarStatus(
                                arquivo.getIdConteudo()
                        );

                Object tipoUsuario =
                        session.getAttribute("tipoUsuario");

                if ("aluno".equals(tipoUsuario)
                        && statusConteudo != null
                        && !"ativo".equalsIgnoreCase(statusConteudo)) {

                    return ResponseEntity
                            .status(403)
                            .build();
                }

            } catch (SQLException e) {

                return ResponseEntity
                        .status(500)
                        .build();
            }
        }

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
     * exclusao
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