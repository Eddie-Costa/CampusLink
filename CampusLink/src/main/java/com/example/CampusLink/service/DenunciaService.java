package com.example.CampusLink.service;

import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dao.denunciaDAO;
import com.example.CampusLink.dto.DenunciaDTO;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class DenunciaService {

    private final denunciaDAO denunciaDAO;
    private final conteudoDAO conteudoDAO;

    public DenunciaService(
            denunciaDAO denunciaDAO,
            conteudoDAO conteudoDAO
    ) {
        this.denunciaDAO = denunciaDAO;
        this.conteudoDAO = conteudoDAO;
    }

    public String registrarDenuncia(
            Long idConteudo,
            Long idAluno,
            Long idTurma,
            String motivo
    ) {

        if (idConteudo == null ||
                idAluno == null ||
                idTurma == null) {

            return "erro";
        }

        if (motivo == null ||
                motivo.isBlank()) {

            return "motivo_vazio";
        }

        try {

            String statusConteudo =
                    conteudoDAO.buscarStatus(
                            idConteudo
                    );

            if (statusConteudo == null) {
                return "conteudo_nao_encontrado";
            }

            if (!statusConteudo.equalsIgnoreCase("ativo")) {
                return "conteudo_indisponivel";
            }

            boolean jaDenunciou =
                    denunciaDAO.alunoJaDenunciou(
                            idConteudo,
                            idAluno
                    );

            if (jaDenunciou) {
                return "duplicada";
            }

            Long idProfessor =
                    conteudoDAO.buscarIdProfessor(
                            idConteudo
                    );

            if (idProfessor == null) {
                return "professor_nao_encontrado";
            }

            int totalAlunos =
                    denunciaDAO.contarAlunosDaTurma(
                            idTurma
                    );

            if (totalAlunos <= 0) {
                return "turma_sem_alunos";
            }

            DenunciaDTO denuncia =
                    new DenunciaDTO();

            denuncia.setIdConteudo(
                    idConteudo
            );

            denuncia.setIdAluno(
                    idAluno
            );

            denuncia.setIdProfessor(
                    idProfessor
            );

            denuncia.setMotivo(
                    motivo.trim()
            );

            denunciaDAO.inserir(
                    denuncia
            );

            int quantidadeDenuncias =
                    denunciaDAO.contarDenunciasPendentes(
                            idConteudo
                    );

            int limiteDenuncias =
                    (int) Math.ceil(
                            totalAlunos * 0.15
                    );

            if (limiteDenuncias < 2) {
                limiteDenuncias = 2;
            }

            if (quantidadeDenuncias >= limiteDenuncias) {

                conteudoDAO.atualizarStatus(
                        idConteudo,
                        "suspenso_denuncia"
                );

                return "suspenso";
            }

            return "sucesso";

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Não foi possível registrar a denúncia",
                    e
            );
        }
    }

    public int contarDenuncias(
            Long idConteudo
    ) {

        try {

            return denunciaDAO.contarDenunciasPendentes(
                    idConteudo
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Não foi possível contar as denúncias",
                    e
            );
        }
    }

    public List<String> listarNomesAlunos(
            Long idConteudo
    ) {

        try {

            return denunciaDAO.listarNomesAlunosPorConteudo(
                    idConteudo
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Não foi possível listar os alunos",
                    e
            );
        }
    }
}