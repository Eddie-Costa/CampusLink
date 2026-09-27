package com.example.CampusLink.service;

import org.slf4j.helpers.MessageFormatter;
import java.io.StringWriter;
import java.io.PrintWriter;
import com.example.CampusLink.dao.usuarioDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dao.denunciaDAO;
import com.example.CampusLink.dto.DenunciaDTO;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class DenunciaService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(DenunciaService.class);

    private final denunciaDAO denunciaDAO;
    private final conteudoDAO conteudoDAO;

    public DenunciaService(
            denunciaDAO denunciaDAO,
            conteudoDAO conteudoDAO
    ) {
        this.denunciaDAO = denunciaDAO;
        this.conteudoDAO = conteudoDAO;
    }

    public String registrarDenuncia(Long idConteudo, Long idAluno, Long idTurma, String motivo) {

        if (idConteudo == null || idAluno == null || idTurma == null) {
            logger.warn("Denúncia recusada: identificadores ausentes. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia recusada: identificadores ausentes. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "erro";
        }

        if (motivo == null || motivo.isBlank()) {
            logger.warn("Denúncia recusada: motivo vazio. conteudoId={} alunoId={}", idConteudo, idAluno);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia recusada: motivo vazio. conteudoId={} alunoId={}", new Object[]{idConteudo, idAluno}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return "motivo_vazio";
        }

        try {

            String statusConteudo = conteudoDAO.buscarStatus(idConteudo);

            if (statusConteudo == null) {
                logger.warn("Conteúdo não encontrado para denúncia. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo não encontrado para denúncia. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "conteudo_nao_encontrado";
            }

            if (!statusConteudo.equalsIgnoreCase("ativo")) {
                logger.warn("Conteúdo indisponível para denúncia. conteudoId={} status={}", idConteudo, statusConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo indisponível para denúncia. conteudoId={} status={}", new Object[]{idConteudo, statusConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "conteudo_indisponivel";
            }

            boolean jaDenunciou = denunciaDAO.alunoJaDenunciou(idConteudo, idAluno);

            if (jaDenunciou) {
                logger.warn("Denúncia duplicada. conteudoId={} alunoId={}", idConteudo, idAluno);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Denúncia duplicada. conteudoId={} alunoId={}", new Object[]{idConteudo, idAluno}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "duplicada";
            }

            Long idProfessor = conteudoDAO.buscarIdProfessor(idConteudo);

            if (idProfessor == null) {
                logger.warn("Professor não encontrado para denúncia. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Professor não encontrado para denúncia. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "professor_nao_encontrado";
            }

            int totalAlunos = denunciaDAO.contarAlunosDaTurma(idTurma);

            if (totalAlunos <= 0) {
                logger.warn("Denúncia recusada: turma sem alunos. turmaId={}", idTurma);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Denúncia recusada: turma sem alunos. turmaId={}", new Object[]{idTurma}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                return "turma_sem_alunos";
            }

            DenunciaDTO denuncia = new DenunciaDTO();
            denuncia.setIdConteudo(idConteudo);
            denuncia.setIdAluno(idAluno);
            denuncia.setIdProfessor(idProfessor);
            denuncia.setMotivo(motivo.trim());
            denunciaDAO.inserir(denuncia);
            logger.info("Denúncia registrada. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Denúncia registrada. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

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

                conteudoDAO.atualizarStatus(idConteudo, "suspenso_denuncia");
                logger.info("Conteúdo suspenso automaticamente por denúncias. conteudoId={} quantidade={} limite={}", idConteudo, quantidadeDenuncias, limiteDenuncias);
                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", DenunciaService.class.getName(), "registrarDenuncia", null,
                                MessageFormatter.arrayFormat("Conteúdo suspenso automaticamente por denúncias. conteudoId={} quantidade={} limite={}", new Object[]{idConteudo, quantidadeDenuncias, limiteDenuncias}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }

                return "suspenso";
            }

            return "sucesso";

        } catch (SQLException e) {
            logger.error("Erro ao registrar denúncia. conteudoId={} alunoId={} turmaId={}", idConteudo, idAluno, idTurma, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "registrarDenuncia", null,
                            MessageFormatter.arrayFormat("Erro ao registrar denúncia. conteudoId={} alunoId={} turmaId={}", new Object[]{idConteudo, idAluno, idTurma}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível registrar a denúncia", e);
        }
    }

    public int contarDenuncias(Long idConteudo) {

        try {
            return denunciaDAO.contarDenunciasPendentes(idConteudo);

        } catch (SQLException e) {
            logger.error("Erro ao contar denúncias. conteudoId={}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "contarDenuncias", null,
                            MessageFormatter.arrayFormat("Erro ao contar denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível contar as denúncias", e);
        }
    }

    public List<String> listarNomesAlunos(Long idConteudo) {

        try {
            return denunciaDAO.listarNomesAlunosPorConteudo(idConteudo);

        } catch (SQLException e) {
            logger.error("Erro ao consultar alunos das denúncias. conteudoId={}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "listarNomesAlunos", null,
                            MessageFormatter.arrayFormat("Erro ao consultar alunos das denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível listar os alunos", e);
        }
    }

    public List<DenunciaDTO> listarPorConteudo(Long idConteudo) {

        try {
            return denunciaDAO.listarPorConteudo(idConteudo);
        } catch (SQLException e) {
            logger.error("Erro ao consultar denúncias. conteudoId={}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", DenunciaService.class.getName(), "listarPorConteudo", null,
                            MessageFormatter.arrayFormat("Erro ao consultar denúncias. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível listar as denúncias do conteúdo", e);
        }
    }
}
