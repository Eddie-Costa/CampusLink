package com.example.CampusLink.service;

import com.example.CampusLink.dao.ArquivoDAO;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.dao.usuarioDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class ArquivoService {

    @Autowired
    private usuarioDAO usuarioDAO;

    private static final Logger logger = LoggerFactory.getLogger(ArquivoService.class);

    private final ArquivoDAO arquivoDAO;
    private final SupabaseStorageService storageService;
    private final String bucket;

    private static final long TAMANHO_MAXIMO =
            6L * 1024 * 1024;

    private static final Set<String> TIPOS_PERMITIDOS =
            Set.of(
                    "application/pdf",
                    "image/png",
                    "image/jpeg",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            );

    public ArquivoService(
            ArquivoDAO arquivoDAO,
            SupabaseStorageService storageService,
            @Value("${supabase.storage.bucket}") String bucket) {

        this.arquivoDAO = arquivoDAO;
        this.storageService = storageService;
        this.bucket = bucket;
    }

    public void salvar(
            MultipartFile arquivo,
            Long idConteudo) {

        validarArquivo(arquivo);

        String storagePath;
        try {
            storagePath = storageService.upload(arquivo);
        } catch (RuntimeException e) {
            logger.error("Erro ao enviar arquivo ao Storage. conteudoId={} tamanhoBytes={}", idConteudo, arquivo.getSize(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
                            MessageFormatter.arrayFormat("Erro ao enviar arquivo ao Storage. conteudoId={} tamanhoBytes={}", new Object[]{idConteudo, arquivo.getSize()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }

        ArquivoDTO arquivoDTO =
                new ArquivoDTO();

        arquivoDTO.setIdConteudo(idConteudo);

        arquivoDTO.setBucketId(bucket);

        arquivoDTO.setStoragePath(
                storagePath
        );

        arquivoDTO.setNomeOriginal(
                arquivo.getOriginalFilename()
        );

        arquivoDTO.setMimeType(
                arquivo.getContentType()
        );

        arquivoDTO.setTamanhoBytes(
                arquivo.getSize()
        );

        try {

            arquivoDAO.inserir(arquivoDTO);
            logger.info("Arquivo salvo no Storage e no banco. conteudoId={} storagePath={} tamanhoBytes={}", idConteudo, storagePath, arquivo.getSize());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "salvar", null,
                            MessageFormatter.arrayFormat("Arquivo salvo no Storage e no banco. conteudoId={} storagePath={} tamanhoBytes={}", new Object[]{idConteudo, storagePath, arquivo.getSize()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao registrar arquivo no banco após upload. conteudoId={} storagePath={}", idConteudo, storagePath, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
                            MessageFormatter.arrayFormat("Erro ao registrar arquivo no banco após upload. conteudoId={} storagePath={}", new Object[]{idConteudo, storagePath}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

            // Storage funcionou, mas banco falhou.
            // Tentamos remover o arquivo que foi enviado.

            try {

                storageService.excluir(
                        storagePath
                );

                logger.info("Arquivo removido do Storage após falha no banco. conteudoId={} storagePath={}", idConteudo, storagePath);
                if (logger.isInfoEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "salvar", null,
                                MessageFormatter.arrayFormat("Arquivo removido do Storage após falha no banco. conteudoId={} storagePath={}", new Object[]{idConteudo, storagePath}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } catch (Exception erroRemocao) {
                logger.error("Erro ao remover arquivo do Storage após falha no banco. conteudoId={} storagePath={}", idConteudo, storagePath, erroRemocao);
                if (logger.isErrorEnabled()) {
                    try {
                        StringWriter excecaoLogBD = new StringWriter();
                        erroRemocao.printStackTrace(new PrintWriter(excecaoLogBD));
                        usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
                                MessageFormatter.arrayFormat("Erro ao remover arquivo do Storage após falha no banco. conteudoId={} storagePath={}", new Object[]{idConteudo, storagePath}).getMessage(), null, excecaoLogBD.toString());
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            }

            throw new RuntimeException(
                    "Não foi possível registrar o arquivo no banco.",
                    e
            );
        }
    }

    public List<ArquivoDTO> listarTodos() {

        try {

            return arquivoDAO.listarTodos();

        } catch (SQLException e) {

            logger.error("Erro ao listar arquivos no banco.", e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "listarTodos", null,
                            "Erro ao listar arquivos no banco.", null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException(
                    "Não foi possível listar os arquivos.",
                    e
            );
        }
    }

    public ArquivoDTO buscarPorId(Long id) {

        try {

            ArquivoDTO arquivo =
                    arquivoDAO.buscarPorId(id);

            if (arquivo == null) {

                logger.warn("Arquivo não encontrado. arquivoId={}", id);
                if (logger.isWarnEnabled()) {
                    try {
                        usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "buscarPorId", null,
                                MessageFormatter.arrayFormat("Arquivo não encontrado. arquivoId={}", new Object[]{id}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
                throw new IllegalArgumentException(
                        "Arquivo não encontrado."
                );
            }

            return arquivo;

        } catch (SQLException e) {

            logger.error("Erro ao buscar arquivo no banco. arquivoId={}", id, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "buscarPorId", null,
                            MessageFormatter.arrayFormat("Erro ao buscar arquivo no banco. arquivoId={}", new Object[]{id}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException(
                    "Erro ao buscar o arquivo.",
                    e
            );
        }
    }
    public ArquivoDTO buscarMaisRecentePorConteudo(Long idConteudo) {

        try {

            return arquivoDAO.buscarMaisRecentePorConteudo(idConteudo);

        } catch (SQLException e) {

            logger.error("Erro ao buscar arquivo do conteúdo. conteudoId={}", idConteudo, e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "buscarMaisRecentePorConteudo", null,
                            MessageFormatter.arrayFormat("Erro ao buscar arquivo do conteúdo. conteudoId={}", new Object[]{idConteudo}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException(
                    "Erro ao buscar o arquivo do conteúdo.",
                    e
            );
        }
    }

    public byte[] download(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);

        try {
            byte[] dados = storageService.download(arquivo.getStoragePath());
            logger.debug("Download de arquivo concluído no servidor. arquivoId={} conteudoId={} tamanhoBytes={}", id, arquivo.getIdConteudo(), dados.length);
            if (logger.isDebugEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "DEBUG", ArquivoService.class.getName(), "download", null,
                            MessageFormatter.arrayFormat("Download de arquivo concluído no servidor. arquivoId={} conteudoId={} tamanhoBytes={}", new Object[]{id, arquivo.getIdConteudo(), dados.length}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return dados;
        } catch (RuntimeException e) {
            logger.error("Erro ao baixar arquivo do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "download", null,
                            MessageFormatter.arrayFormat("Erro ao baixar arquivo do Storage. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    public void excluir(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);

        // Primeiro removemos o arquivo físico.

        try {
            storageService.excluir(arquivo.getStoragePath());
        } catch (RuntimeException e) {
            logger.error("Erro ao excluir arquivo do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir arquivo do Storage. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }

        // Depois removemos o registro do banco.

        try {

            arquivoDAO.excluir(id);
            logger.info("Arquivo excluído do Storage e do banco. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo());
            if (logger.isInfoEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Arquivo excluído do Storage e do banco. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {

            logger.error("Erro ao excluir registro do banco após remoção do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            if (logger.isErrorEnabled()) {
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    usuarioDAO.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir registro do banco após remoção do Storage. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new RuntimeException(
                    "O arquivo foi removido do Storage, "
                            + "mas ocorreu um erro ao remover "
                            + "o registro do banco.",
                    e
            );
        }
    }

    private void validarArquivo(
            MultipartFile arquivo) {

        if (arquivo == null
                || arquivo.isEmpty()) {

            logger.warn("Upload recusado: arquivo ausente ou vazio.");
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "validarArquivo", null,
                            "Upload recusado: arquivo ausente ou vazio.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new IllegalArgumentException(
                    "Selecione um arquivo."
            );
        }

        if (arquivo.getSize()
                > TAMANHO_MAXIMO) {

            logger.warn("Upload recusado: tamanho acima do permitido. tamanhoBytes={} limiteBytes={}", arquivo.getSize(), TAMANHO_MAXIMO);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "validarArquivo", null,
                            MessageFormatter.arrayFormat("Upload recusado: tamanho acima do permitido. tamanhoBytes={} limiteBytes={}", new Object[]{arquivo.getSize(), TAMANHO_MAXIMO}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new IllegalArgumentException(
                    "O arquivo deve possuir no máximo 6 MB."
            );
        }

        String mimeType =
                arquivo.getContentType();

        if (mimeType == null
                || !TIPOS_PERMITIDOS.contains(mimeType)) {

            logger.warn("Upload recusado: tipo de arquivo não permitido. mimeType={}", mimeType);
            if (logger.isWarnEnabled()) {
                try {
                    usuarioDAO.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "validarArquivo", null,
                            MessageFormatter.arrayFormat("Upload recusado: tipo de arquivo não permitido. mimeType={}", new Object[]{mimeType}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido."
            );
        }
    }
}
