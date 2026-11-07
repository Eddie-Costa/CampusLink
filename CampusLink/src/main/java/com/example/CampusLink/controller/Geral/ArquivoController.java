// pagina de arquivos recebe pedidos de consulta envio download e exclusao
package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.ProfessorRepository;
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
import java.util.ArrayList;
import java.util.List;

// controle dos materiais enviados esta classe verifica o perfil antes de encaminhar as acoes dos arquivos
@Controller
@RequestMapping("/arquivos")
public class ArquivoController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ArquivoController.class);

    private final ArquivoService arquivoService;
    private final ConteudoRepository conteudoRepository;
    private final AlunoRepository alunoRepository;
    private final ProfessorRepository professorRepository;

    // preparar controle de arquivos este construtor recebe os servicos de arquivos e conteudos
    public ArquivoController(
            ArquivoService arquivoService,
            ConteudoRepository conteudoRepository,
            AlunoRepository alunoRepository,
            ProfessorRepository professorRepository) {

        this.arquivoService = arquivoService;
        this.conteudoRepository = conteudoRepository;
        this.alunoRepository = alunoRepository;
        this.professorRepository = professorRepository;
    }

    private boolean podeAcessar(HttpSession session, Long idConteudo) throws SQLException {
        if (idConteudo == null) {
            return false;
        }

        Object usuario = session.getAttribute("usuarioLogado");
        String tipo = null;
        String idUsuario = null;
        if (usuario instanceof loginAlunoDTO aluno && "aluno".equals(session.getAttribute("tipoUsuario"))) {
            tipo = "aluno";
            idUsuario = alunoRepository.buscarPorIDAluno(aluno.getEmail());
        } else if (usuario instanceof loginProfessorDTO professor && "professor".equals(session.getAttribute("tipoUsuario"))) {
            tipo = "professor";
            idUsuario = professorRepository.buscarPorIDProfessor(professor.getEmail());
        }

        return idUsuario != null && !idUsuario.isBlank()
                && conteudoRepository.usuarioTemAcessoAoConteudo(idConteudo, tipo, Long.parseLong(idUsuario));
    }

    private void registrarRecusa(String operacao, Long idArquivo, Long idConteudo) {
        logger.warn("[{}] acesso a arquivo recusado: usuário sem vínculo. arquivoId={} conteudoId={}", operacao, idArquivo, idConteudo);
        if (logger.isWarnEnabled()) {
            try {
                UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoController.class.getName(), operacao, null,
                        MessageFormatter.arrayFormat("[{}] acesso a arquivo recusado: usuário sem vínculo. arquivoId={} conteudoId={}", new Object[]{operacao, idArquivo, idConteudo}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
    }

    private boolean professorLogado(HttpSession session) {
        return session.getAttribute("usuarioLogado") instanceof loginProfessorDTO
                && "professor".equals(session.getAttribute("tipoUsuario"));
    }





    // listar arquivos na tela este metodo mostra os materiais para quem esta conectado
    @GetMapping
    public String pagina(
            Model model,
            HttpSession session) throws SQLException {

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

        List<ArquivoDTO> visiveis = new ArrayList<>();
        for (ArquivoDTO arquivo : arquivoService.listarTodos()) {
            if (podeAcessar(session, arquivo.getIdConteudo())) {
                visiveis.add(arquivo);
            }
        }

        model.addAttribute(
                "arquivos",
                visiveis
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

            RedirectAttributes redirectAttributes) throws SQLException {

        // restringir envio de materiais recusa o envio quando nao ha um professor com login completo
        if (!professorLogado(session)) {

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

        // exigir conteudo do arquivo recusa envios sem conteudo ou de conteudos sem vinculo com o professor
        if (idConteudo == null) {
            redirectAttributes.addFlashAttribute("erro", "Informe o conteúdo ao qual o arquivo pertence.");
            return "redirect:/arquivos";
        }
        if (!podeAcessar(session, idConteudo)) {
            registrarRecusa("upload", null, idConteudo);
            redirectAttributes.addFlashAttribute("erro", "Você não tem acesso a este conteúdo.");
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

        ArquivoDTO arquivo;
        try {
            arquivo = arquivoService.buscarPorId(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(404)
                    .build();
        }

        if (arquivo.getIdConteudo() == null) {
            registrarRecusa("download", id, null);
            return ResponseEntity
                    .status(403)
                    .build();
        }

        // verificar conteudo do arquivo consulta a situacao quando o arquivo possui conteudo associado
        if (arquivo.getIdConteudo() != null) {

            // consultar acesso ao material verifica a situacao do conteudo e retorna erro se a consulta falhar
            try {

                if (!podeAcessar(session, arquivo.getIdConteudo())) {
                    registrarRecusa("download", id, arquivo.getIdConteudo());
                    return ResponseEntity
                            .status(403)
                            .build();
                }

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
            RedirectAttributes redirectAttributes) throws SQLException {

        // proteger delete arquivo recusa a exclusao quando nao ha um professor com login completo
        if (!professorLogado(session)) {

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

        // conferir arquivo excluido recusa arquivos inexistentes ou de conteudos sem vinculo com o professor
        ArquivoDTO arquivoExcluido;
        try {
            arquivoExcluido = arquivoService.buscarPorId(id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", "Arquivo não encontrado.");
            return "redirect:/arquivos";
        }
        if (!podeAcessar(session, arquivoExcluido.getIdConteudo())) {
            registrarRecusa("excluir", id, arquivoExcluido.getIdConteudo());
            redirectAttributes.addFlashAttribute("erro", "Você não tem acesso a este arquivo.");
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
