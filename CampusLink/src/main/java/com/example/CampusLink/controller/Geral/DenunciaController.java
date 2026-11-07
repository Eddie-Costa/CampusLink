// denuncias de materiais recebe os relatos enviados pelos alunos
package com.example.CampusLink.controller.Geral;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.repository.AlunoRepository;
import com.example.CampusLink.repository.ConteudoRepository;
import com.example.CampusLink.repository.TurmaRepository;
import com.example.CampusLink.dto.TurmaDTO;
import com.example.CampusLink.service.DenunciaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.List;

// controle das denuncias esta classe verifica aluno turma e conteudo antes de registrar o motivo
@Controller
public class DenunciaController {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(DenunciaController.class);

    private final DenunciaService denunciaService;
    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;
    private final ConteudoRepository conteudoRepository;

    // preparar controle de denuncias este construtor recebe os dados usados para conferir aluno turma e conteudo
    public DenunciaController(
            DenunciaService denunciaService,
            AlunoRepository alunoRepository,
            TurmaRepository turmaRepository,
            ConteudoRepository conteudoRepository
    ) {
        this.denunciaService = denunciaService;
        this.alunoRepository = alunoRepository;
        this.turmaRepository = turmaRepository;
        this.conteudoRepository = conteudoRepository;
    }

    // denunciar conteudo pela tela este metodo confere a participacao do aluno e registra o motivo
    @PostMapping("/turmas/{idTurma}/conteudos/{idConteudo}/denunciar")
    public String denunciarConteudo(
            @PathVariable Long idTurma,
            @PathVariable Long idConteudo,
            @RequestParam("motivo") String motivo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        // proteger denuncia exige login antes de receber uma denuncia de conteudo
        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

        // restringir denuncia permite o envio apenas pelo perfil de aluno
        if (session.getAttribute("tipoUsuario") == null ||
                !"aluno".equalsIgnoreCase(
                        session.getAttribute("tipoUsuario").toString()
                )) {

            redirectAttributes.addFlashAttribute(
                    "mensagemErro",
                    "Apenas alunos podem denunciar conteúdos."
            );

            return "redirect:/turmas/" + idTurma;
        }

        String email =
                (String) session.getAttribute("email2FA");

        // exigir email da denuncia retorna ao login quando o email da sessao esta ausente
        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        // preparar denuncia confere aluno turma e conteudo e trata falhas na consulta ou no codigo
        try {

            String idAlunoTexto =
                    alunoRepository.buscarPorIDAluno(email);

            // identificar aluno denunciante interrompe a denuncia quando nao encontra o codigo do aluno
            if (idAlunoTexto == null ||
                    idAlunoTexto.isBlank()) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Não foi possível identificar o aluno."
                );

                return "redirect:/turmas/" + idTurma;
            }

            Long idAluno =
                    Long.parseLong(idAlunoTexto);

            List<TurmaDTO> turmasAluno =
                    turmaRepository.buscarTurmasDoUsuario(
                            "aluno",
                            idAlunoTexto
                    );

            boolean alunoPertenceTurma = false;

            for (TurmaDTO turma : turmasAluno) {

                // localizar turma do aluno confirma o vinculo ao encontrar a turma solicitada
                if (turma.getId() != null &&
                        turma.getId().equals(idTurma)) {

                    alunoPertenceTurma = true;
                    break;
                }
            }

            // impedir denuncia fora da turma recusa alunos que nao participam da turma
            if (!alunoPertenceTurma) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Você não participa desta turma."
                );

                return "redirect:/turmas";
            }

            boolean conteudoPertenceTurma =
                    conteudoRepository.conteudoPertenceTurma(
                            idConteudo,
                            idTurma
                    );

            // conferir conteudo denunciado recusa um material que nao pertence a turma informada
            if (!conteudoPertenceTurma) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Este conteúdo não pertence à turma."
                );

                return "redirect:/turmas/" + idTurma;
            }

            String resultado =
                    denunciaService.registrarDenuncia(
                            idConteudo,
                            idAluno,
                            idTurma,
                            motivo
                    );

            // confirmar denuncia registrada mostra a mensagem de sucesso quando o registro termina
            if ("sucesso".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemSucesso",
                        "Denúncia registrada com sucesso."
                );

            }
            // informar suspensao do conteudo avisa que a denuncia levou o material para analise
            else if ("suspenso".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemSucesso",
                        "Conteúdo suspenso para análise."
                );

            }
            // impedir denuncia repetida avisa que o aluno ja denunciou o conteudo
            else if ("duplicada".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Você já denunciou este conteúdo."
                );

            }
            // exigir motivo da denuncia pede uma justificativa quando o motivo esta vazio
            else if ("motivo_vazio".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Informe o motivo da denúncia."
                );

            }
            // informar conteudo ausente avisa que o material denunciado nao foi encontrado
            else if ("conteudo_nao_encontrado".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Conteúdo não encontrado."
                );

            }
            // informar denuncia indisponivel avisa que o material nao aceita denuncias nesta situacao
            else if ("conteudo_indisponivel".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Este conteúdo não está disponível para denúncia."
                );

            }
            // informar professor ausente avisa que nao foi possivel identificar o responsavel pelo conteudo
            else if ("professor_nao_encontrado".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Não foi possível identificar o professor responsável."
                );

            }
            // informar alunos ausentes distingue falta de alunos de outras falhas da denuncia
            else if ("turma_sem_alunos".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Não foi possível identificar os alunos da turma."
                );

            } else {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Não foi possível registrar a denúncia."
                );
            }

        } catch (SQLException | NumberFormatException e) {
            logger.error("Erro ao preparar denúncia. conteudoId={} turmaId={}", idConteudo, idTurma, e);
            // registro de erros confere se esta habilitado ao denunciar conteudo pela tela
            if (logger.isErrorEnabled()) {
                // gravar historico ao denunciar conteudo pela tela tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", DenunciaController.class.getName(), "denunciarConteudo", null,
                            MessageFormatter.arrayFormat("Erro ao preparar denúncia. conteudoId={} turmaId={}", new Object[]{idConteudo, idTurma}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            redirectAttributes.addFlashAttribute(
                    "mensagemErro",
                    "Não foi possível registrar a denúncia."
            );
        }

        return "redirect:/turmas/" + idTurma;
    }
}
