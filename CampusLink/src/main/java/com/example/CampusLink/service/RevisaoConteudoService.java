package com.example.CampusLink.service;

import org.slf4j.helpers.MessageFormatter;
import com.example.CampusLink.dao.usuarioDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CampusLink.dao.RevisaoConteudoDAO;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
public class RevisaoConteudoService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(RevisaoConteudoService.class);

    private final RevisaoConteudoDAO revisaoConteudoDAO;

    public RevisaoConteudoService(RevisaoConteudoDAO revisaoConteudoDAO) {
        this.revisaoConteudoDAO = revisaoConteudoDAO;
    }

    public boolean liberarConteudo(Long idConteudo) {

        try {
            boolean liberado = revisaoConteudoDAO.liberarConteudo(idConteudo);
            if (liberado) {
                logger.info("Conteúdo liberado pelo administrador. conteudoId={}", idConteudo);
                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "liberarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo liberado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Liberação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "liberarConteudo", null,
                                MessageFormatter.arrayFormat("Liberação recusada: conteúdo não aguarda análise. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return liberado;
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível liberar o conteúdo.", e);
        }
    }

    public boolean reprovarConteudo(Long idConteudo, String comentarioAdmin) {

        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            logger.warn("Reprovação recusada: motivo vazio. conteudoId={}", idConteudo);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("Reprovação recusada: motivo vazio. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return false;
        }

        try {
            boolean reprovado = revisaoConteudoDAO.reprovarConteudo(idConteudo, comentarioAdmin);
            if (reprovado) {
                logger.info("Conteúdo reprovado pelo administrador. conteudoId={}", idConteudo);
                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo reprovado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Reprovação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                                MessageFormatter.arrayFormat("Reprovação recusada: conteúdo não aguarda análise. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return reprovado;
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível reprovar o conteúdo.", e);
        }
    }
}
