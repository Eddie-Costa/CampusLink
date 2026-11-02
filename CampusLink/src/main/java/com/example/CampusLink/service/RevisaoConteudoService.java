package com.example.CampusLink.service;

import org.slf4j.helpers.MessageFormatter;
import com.example.CampusLink.repository.UsuarioRepository;
import com.example.CampusLink.repository.RevisaoConteudoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevisaoConteudoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(RevisaoConteudoService.class);

    private final RevisaoConteudoRepository revisaoConteudoRepository;

    public RevisaoConteudoService(RevisaoConteudoRepository revisaoConteudoRepository) {
        this.revisaoConteudoRepository = revisaoConteudoRepository;
    }

    // conteudo e denuncias mudam juntos ou nao mudam
    @Transactional
    public boolean liberarConteudo(Long idConteudo) {

        try {
            boolean liberado = false;
            if (idConteudo != null) {
                liberado = revisaoConteudoRepository.liberarConteudo(idConteudo) > 0;
                if (liberado) {
                    revisaoConteudoRepository.marcarDenunciasLiberadas(idConteudo);
                }
            }

            if (liberado) {
                logger.info("Conteúdo liberado pelo administrador. conteudoId={}", idConteudo);
                if (logger.isInfoEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "liberarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo liberado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Liberação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "liberarConteudo", null,
                                MessageFormatter.arrayFormat("Liberação recusada: conteúdo não aguarda análise. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return liberado;
        } catch (DataAccessException e) {
            throw new RuntimeException("Não foi possível liberar o conteúdo.", e);
        }
    }

    // conteudo e denuncias mudam juntos ou nao mudam
    @Transactional
    public boolean reprovarConteudo(Long idConteudo, String comentarioAdmin) {

        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            logger.warn("Reprovação recusada: motivo vazio. conteudoId={}", idConteudo);
            if (logger.isWarnEnabled()) {
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("Reprovação recusada: motivo vazio. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return false;
        }

        try {
            boolean reprovado = false;
            if (idConteudo != null) {
                reprovado = revisaoConteudoRepository.reprovarConteudo(idConteudo, comentarioAdmin.trim()) > 0;
                if (reprovado) {
                    revisaoConteudoRepository.marcarDenunciasRemovidas(idConteudo);
                }
            }

            if (reprovado) {
                logger.info("Conteúdo reprovado pelo administrador. conteudoId={}", idConteudo);
                if (logger.isInfoEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo reprovado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Reprovação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                if (logger.isWarnEnabled()) {
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                                MessageFormatter.arrayFormat("Reprovação recusada: conteúdo não aguarda análise. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }
            return reprovado;
        } catch (DataAccessException e) {
            throw new RuntimeException("Não foi possível reprovar o conteúdo.", e);
        }
    }
}