package com.example.CampusLink.service;

import com.example.CampusLink.dao.conteudoDAO;
import com.example.CampusLink.dto.ConteudoDTO;
import com.example.CampusLink.dao.usuarioDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.SQLException;
import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class ConteudoService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(ConteudoService.class);

    private final conteudoDAO conteudoDAO;
    private final ArquivoService arquivoService;

    public ConteudoService(
            com.example.CampusLink.dao.conteudoDAO conteudoDAO,
            ArquivoService arquivoService) {

        this.conteudoDAO = conteudoDAO;
        this.arquivoService = arquivoService;
    }
    public ConteudoDTO criar(ConteudoDTO conteudo, MultipartFile arquivo) {

        if (conteudo.getPrioridade() == null || conteudo.getPrioridade().isBlank()) {
            conteudo.setPrioridade("media");
        }
        conteudo.setTipo(definirTipo(conteudo, arquivo));
        try {
            conteudoDAO.inserir(conteudo);
        } catch (SQLException e) {
            logger.error("Erro ao cadastrar conteúdo. turmaId={} professorId={}", conteudo.getIdTurma(), conteudo.getIdProfessor(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                            MessageFormatter.arrayFormat("Erro ao cadastrar conteúdo. turmaId={} professorId={}", new Object[]{conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível cadastrar o conteúdo.", e);
        }
        if (arquivo != null && !arquivo.isEmpty()) {
            try {
                arquivoService.salvar(arquivo, conteudo.getId());
            } catch (RuntimeException e) {
                // Conteúdo já foi criado, mas o arquivo falhou.
                // Não vamos deixar um conteúdo "quebrado" sem o arquivo.
                try {
                    conteudoDAO.excluir(conteudo.getId());
                    logger.info("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma());
                    if (logger.isInfoEnabled()) {
                        try {
                            usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Conteúdo removido após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, null);
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                } catch (SQLException erroRemocao) {
                    logger.error("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", conteudo.getId(), conteudo.getIdTurma(), erroRemocao);
                    if (logger.isErrorEnabled()) {
                        try {
                            StringWriter excecaoLogBD = new StringWriter();
                            erroRemocao.printStackTrace(new PrintWriter(excecaoLogBD));
                            usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "criar", null,
                                    MessageFormatter.arrayFormat("Erro ao remover conteúdo após falha no arquivo. conteudoId={} turmaId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma()}).getMessage(), null, excecaoLogBD.toString());
                        } catch (Exception erroLogBD) {
                            logger.error("Erro ao gravar log no banco.", erroLogBD);
                        }
                    }
                }
                throw e;
            }
        }

        logger.info("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor());
        if (logger.isInfoEnabled()) {
            try {
                usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "criar", null,
                        MessageFormatter.arrayFormat("Conteúdo cadastrado. conteudoId={} turmaId={} professorId={}", new Object[]{conteudo.getId(), conteudo.getIdTurma(), conteudo.getIdProfessor()}).getMessage(), null, null);
            } catch (Exception erroLogBD) {
                logger.error("Erro ao gravar log no banco.", erroLogBD);
            }
        }
        return conteudo;
    }

    public List<ConteudoDTO> listarPorTurma(Long idTurma) {

        try {
            return conteudoDAO.listarPorTurma(idTurma);
        } catch (SQLException e) {
            logger.error("Erro ao listar conteúdos. turmaId={}", idTurma, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "listarPorTurma", null,
                            MessageFormatter.arrayFormat("Erro ao listar conteúdos. turmaId={}", new Object[]{idTurma}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível listar os conteúdos da turma.", e);
        }
    }
    public void excluir(Long id) {

        try {
            conteudoDAO.excluir(id);
            logger.info("Conteúdo excluído. conteudoId={}", id);
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Conteúdo excluído. conteudoId={}", new Object[]{id}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao excluir conteúdo. conteudoId={}", id, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir conteúdo. conteudoId={}", new Object[]{id}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível excluir o conteúdo.", e);
        }
    }
    public ConteudoDTO atualizar(ConteudoDTO conteudo) {

        conteudo.setTipo(definirTipo(conteudo, null));

        try {
            conteudoDAO.atualizar(conteudo);
            logger.info("Conteúdo atualizado. conteudoId={}", conteudo.getId());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Conteúdo atualizado. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao atualizar conteúdo. conteudoId={}", conteudo.getId(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ConteudoService.class.getName(), "atualizar", null,
                            MessageFormatter.arrayFormat("Erro ao atualizar conteúdo. conteudoId={}", new Object[]{conteudo.getId()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException("Não foi possível atualizar o conteúdo.", e);
        }

        return conteudo;
    }

    private String definirTipo(ConteudoDTO conteudo, MultipartFile arquivo) {

        if (arquivo != null && !arquivo.isEmpty()) {
            return "arquivo";
        }

        if (conteudo.getUrl() != null && !conteudo.getUrl().isBlank()) {
            return "link";
        }

        return "texto";
    }
}
