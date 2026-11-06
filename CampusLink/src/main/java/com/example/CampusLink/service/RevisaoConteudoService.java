// revisao administrativa coordena as decisoes sobre materiais suspensos
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

// servico de moderacao esta classe libera ou remove o conteudo e resolve suas denuncias juntas
@Service
public class RevisaoConteudoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(RevisaoConteudoService.class);

    private final RevisaoConteudoRepository revisaoConteudoRepository;

    // preparar revisao de materiais este construtor recebe as operacoes de analise e denuncias
    public RevisaoConteudoService(RevisaoConteudoRepository revisaoConteudoRepository) {
        this.revisaoConteudoRepository = revisaoConteudoRepository;
    }


    // liberar material e denuncias este metodo reativa o conteudo em revisao e resolve os relatos pendentes
    @Transactional
    public boolean liberarConteudo(Long idConteudo) {

        // liberar conteudo tenta aprovar o material e atualizar suas denuncias
        try {
            boolean liberado = false;
            // aprovar conteudo identificado realiza a liberacao quando existe um codigo
            if (idConteudo != null) {
                liberado = revisaoConteudoRepository.liberarConteudo(idConteudo) > 0;
                // atualizar denuncias liberadas marca as denuncias depois de liberar o conteudo
                if (liberado) {
                    revisaoConteudoRepository.marcarDenunciasLiberadas(idConteudo);
                }
            }

            // conferir liberacao do conteudo registra se a aprovacao foi aceita ou recusada
            if (liberado) {
                logger.info("Conteúdo liberado pelo administrador. conteudoId={}", idConteudo);
                // registro de informacoes confere se esta habilitado ao liberar material e denuncias
                if (logger.isInfoEnabled()) {
                    // gravar historico ao liberar material e denuncias tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "liberarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo liberado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Liberação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                // registro de avisos confere se esta habilitado ao liberar material e denuncias
                if (logger.isWarnEnabled()) {
                    // gravar historico ao liberar material e denuncias tenta salvar o registro no banco e informa falhas
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


    // reprovar material e denuncias este metodo exige um motivo remove o conteudo e resolve os relatos pendentes
    @Transactional
    public boolean reprovarConteudo(Long idConteudo, String comentarioAdmin) {

        // justificar reprovacao do conteudo rejeita a reprovacao sem motivo
        if (comentarioAdmin == null || comentarioAdmin.isBlank()) {
            logger.warn("Reprovação recusada: motivo vazio. conteudoId={}", idConteudo);
            // registro de avisos confere se esta habilitado ao reprovar material e denuncias
            if (logger.isWarnEnabled()) {
                // gravar historico ao reprovar material e denuncias tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                            MessageFormatter.arrayFormat("Reprovação recusada: motivo vazio. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return false;
        }

        // reprovar conteudo tenta salvar a decisao e atualizar suas denuncias
        try {
            boolean reprovado = false;
            // reprovar conteudo identificado realiza a reprovacao quando existe um codigo
            if (idConteudo != null) {
                reprovado = revisaoConteudoRepository.reprovarConteudo(idConteudo, comentarioAdmin.trim()) > 0;
                // atualizar denuncias do conteudo reprovado marca as denuncias apos a reprovacao
                if (reprovado) {
                    revisaoConteudoRepository.marcarDenunciasRemovidas(idConteudo);
                }
            }

            // conferir reprovacao do conteudo registra se a decisao foi aceita ou recusada
            if (reprovado) {
                logger.info("Conteúdo reprovado pelo administrador. conteudoId={}", idConteudo);
                // registro de informacoes confere se esta habilitado ao reprovar material e denuncias
                if (logger.isInfoEnabled()) {
                    // gravar historico ao reprovar material e denuncias tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", RevisaoConteudoService.class.getName(), "reprovarConteudo", null,
                                MessageFormatter.arrayFormat("Conteúdo reprovado pelo administrador. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } else {
                logger.warn("Reprovação recusada: conteúdo não aguarda análise. conteudoId={}", idConteudo);
                // registro de avisos confere se esta habilitado ao reprovar material e denuncias
                if (logger.isWarnEnabled()) {
                    // gravar historico ao reprovar material e denuncias tenta salvar o registro no banco e informa falhas
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