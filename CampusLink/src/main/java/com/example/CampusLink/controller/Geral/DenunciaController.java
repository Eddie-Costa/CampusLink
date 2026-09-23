package com.example.CampusLink.controller.Geral;

import com.example.CampusLink.dao.alunoDAO;
import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dao.turmaDAO;
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

@Controller
public class DenunciaController {

    private final DenunciaService denunciaService;
    private final alunoDAO alunoDAO;
    private final turmaDAO turmaDAO;
    private final conteudoDAO conteudoDAO;

    public DenunciaController(
            DenunciaService denunciaService,
            alunoDAO alunoDAO,
            turmaDAO turmaDAO,
            conteudoDAO conteudoDAO
    ) {
        this.denunciaService = denunciaService;
        this.alunoDAO = alunoDAO;
        this.turmaDAO = turmaDAO;
        this.conteudoDAO = conteudoDAO;
    }

    @PostMapping("/turmas/{idTurma}/conteudos/{idConteudo}/denunciar")
    public String denunciarConteudo(
            @PathVariable Long idTurma,
            @PathVariable Long idConteudo,
            @RequestParam("motivo") String motivo,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {

        if (session.getAttribute("usuarioLogado") == null) {
            return "redirect:/login";
        }

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

        if (email == null || email.isBlank()) {
            return "redirect:/login";
        }

        try {

            String idAlunoTexto =
                    alunoDAO.buscarPorIDAluno(email);

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
                    turmaDAO.buscarTurmasDoUsuario(
                            "aluno",
                            idAlunoTexto
                    );

            boolean alunoPertenceTurma = false;

            for (TurmaDTO turma : turmasAluno) {

                if (turma.getId() != null &&
                        turma.getId().equals(idTurma)) {

                    alunoPertenceTurma = true;
                    break;
                }
            }

            if (!alunoPertenceTurma) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Você não participa desta turma."
                );

                return "redirect:/turmas";
            }

            boolean conteudoPertenceTurma =
                    conteudoDAO.conteudoPertenceTurma(
                            idConteudo,
                            idTurma
                    );

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

            if ("sucesso".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemSucesso",
                        "Denúncia registrada com sucesso."
                );

            } else if ("suspenso".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemSucesso",
                        "Conteúdo suspenso para análise."
                );

            } else if ("duplicada".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Você já denunciou este conteúdo."
                );

            } else if ("motivo_vazio".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Informe o motivo da denúncia."
                );

            } else if ("conteudo_nao_encontrado".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Conteúdo não encontrado."
                );

            } else if ("conteudo_indisponivel".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Este conteúdo não está disponível para denúncia."
                );

            } else if ("professor_nao_encontrado".equals(resultado)) {

                redirectAttributes.addFlashAttribute(
                        "mensagemErro",
                        "Não foi possível identificar o professor responsável."
                );

            } else if ("turma_sem_alunos".equals(resultado)) {

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

            redirectAttributes.addFlashAttribute(
                    "mensagemErro",
                    "Não foi possível registrar a denúncia."
            );
        }

        return "redirect:/turmas/" + idTurma;
    }
}