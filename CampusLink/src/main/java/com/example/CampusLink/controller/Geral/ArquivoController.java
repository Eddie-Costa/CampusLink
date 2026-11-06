// pagina de arquivos recebe pedidos de consulta envio download e exclusao
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.service.ArquivoService;
import com.example.CampusLink.repository.UsuarioRepository;

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
import java.sql.SQLException;

// controle dos materiais enviados esta classe verifica o perfil antes de encaminhar as acoes dos arquivos
@Controller
@RequestMapping("/arquivos")
public class ArquivoController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ArquivoController.class);

    private final ArquivoService arquivoService;
    private final ConteudoRepository conteudoRepository;

    // preparar controle de arquivos este construtor recebe os servicos de arquivos e conteudos
    public ArquivoController(
            ArquivoService arquivoService,
            ConteudoRepository conteudoRepository) {

        this.arquivoService = arquivoService;
        this.conteudoRepository = conteudoRepository;
    }





    // listar arquivos na tela este metodo mostra os materiais para quem esta conectado
    @GetMapping
    public String pagina(
            Model model,
            HttpSession session) {

        // proteger pagina de arquivos envia usuarios sem login para a tela de acesso
        if (session.getAttribute("usuarioLogado") == null) {
            logger.warn("Acesso aos arquivos recusado: usuário não autenticado.");
            // registro de avisos confere se esta habilitado ao listar arquivos na tela
            if (logger.isWarnEnabled()) {
                // gravar historico ao listar arquivos na tela tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "pagina", null,
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





    // enviar arquivo pela tela este metodo permite que o professor envie um material
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

        // restringir envio de materiais recusa o envio quando o perfil nao e professor
        if (!"professor".equals(
                session.getAttribute("tipoUsuario"))) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Apenas professores podem enviar materiais."
            );

            logger.warn("Upload recusado: usuário não é professor. conteudoId={}", idConteudo);
            // registro de avisos confere se esta habilitado ao enviar arquivo pela tela
            if (logger.isWarnEnabled()) {
                // gravar historico ao enviar arquivo pela tela tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "upload", null,
                            MessageFormatter.arrayFormat("Upload recusado: usuário não é professor. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/arquivos";
        }

        // salvar material tenta guardar o arquivo e mostra sucesso ou o erro recebido
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





    // baixar arquivo pela tela este metodo verifica o acesso e entrega o arquivo para download
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            HttpSession session) {

        // proteger download recusa a solicitacao quando nao existe usuario conectado
        if (session.getAttribute("usuarioLogado") == null) {

            logger.warn("Download recusado: usuário não autenticado. arquivoId={}", id);
            // registro de avisos confere se esta habilitado ao baixar arquivo pela tela
            if (logger.isWarnEnabled()) {
                // gravar historico ao baixar arquivo pela tela tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "download", null,
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

        // verificar conteudo do arquivo consulta a situacao quando o arquivo possui conteudo associado
        if (arquivo.getIdConteudo() != null) {

            // consultar acesso ao material verifica a situacao do conteudo e retorna erro se a consulta falhar
            try {

                String statusConteudo =
                        conteudoRepository.buscarStatus(
                                arquivo.getIdConteudo()
                        );

                Object tipoUsuario =
                        session.getAttribute("tipoUsuario");

                // bloquear material indisponivel impede que alunos baixem conteudos que nao estao ativos
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





    // delete arquivo pela tela este metodo permite que o professor solicite a exclusao do material
    @PostMapping("/{id}/excluir")
    public String excluir(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // proteger delete arquivo recusa a exclusao para perfis diferentes de professor
        if (!"professor".equals(
                session.getAttribute("tipoUsuario"))) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    "Apenas professores podem excluir materiais."
            );

            logger.warn("Exclusão de arquivo recusada: usuário não é professor. arquivoId={}", id);
            // registro de avisos confere se esta habilitado ao delete arquivo pela tela
            if (logger.isWarnEnabled()) {
                // gravar historico ao delete arquivo pela tela tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Exclusão de arquivo recusada: usuário não é professor. arquivoId={}", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "redirect:/arquivos";
        }

        // delete arquivo tenta excluir o material e mostra sucesso ou o erro recebido
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
