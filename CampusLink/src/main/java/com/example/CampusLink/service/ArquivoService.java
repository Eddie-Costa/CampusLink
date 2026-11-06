// regras de arquivos coordenam validacao envio consulta e exclusao
package com.example.CampusLink.service;

import com.example.CampusLink.repository.ArquivoRepository;
import com.example.CampusLink.dto.ArquivoDTO;
import com.example.CampusLink.repository.UsuarioRepository;
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

// servico de anexos esta classe verifica o material e combina armazenamento externo com registro no banco
@Service
public class ArquivoService {

    @Autowired
    private UsuarioRepository UsuarioRepository;

    private static final Logger logger = LoggerFactory.getLogger(ArquivoService.class);

    private final ArquivoRepository arquivoRepository;
    private final SupabaseStorageService storageService;
    private final String bucket;

    private static final long TAMANHO_MAXIMO =
            50L * 1024 * 1024;

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
    private static final Set<String> EXTENSOES_PERMITIDAS =
            Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx", "ppt", "pptx");

    // preparar servico de arquivos este construtor recebe o banco o armazenamento e a pasta de destino
    public ArquivoService(
            ArquivoRepository arquivoRepository,
            SupabaseStorageService storageService,
            @Value("${supabase.storage.bucket}") String bucket) {

        this.arquivoRepository = arquivoRepository;
        this.storageService = storageService;
        this.bucket = bucket;
    }

    // salvar arquivo completo este metodo valida envia e registra o material e tenta desfazer o envio se o banco falhar
    public void salvar(
            MultipartFile arquivo,
            Long idConteudo) {

        validarArquivo(arquivo);

        String storagePath;
        // enviar arquivo tenta fazer o envio ao armazenamento e registra falhas
        try {
            storagePath = storageService.upload(arquivo);
        } catch (RuntimeException e) {
            logger.error("Erro ao enviar arquivo ao Storage. conteudoId={} tamanhoBytes={}", idConteudo, arquivo.getSize(), e);
            // registro de erros confere se esta habilitado ao salvar arquivo completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao salvar arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
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

        // cadastrar arquivo enviado tenta gravar os dados no banco depois do envio
        try {

            arquivoRepository.inserir(arquivoDTO);
            logger.info("Arquivo salvo no Storage e no banco. conteudoId={} storagePath={} tamanhoBytes={}", idConteudo, storagePath, arquivo.getSize());
            // registro de informacoes confere se esta habilitado ao salvar arquivo completo
            if (logger.isInfoEnabled()) {
                // gravar historico ao salvar arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "salvar", null,
                            MessageFormatter.arrayFormat("Arquivo salvo no Storage e no banco. conteudoId={} storagePath={} tamanhoBytes={}", new Object[]{idConteudo, storagePath, arquivo.getSize()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao registrar arquivo no banco após upload. conteudoId={} storagePath={}", idConteudo, storagePath, e);
            // registro de erros confere se esta habilitado ao salvar arquivo completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao salvar arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
                            MessageFormatter.arrayFormat("Erro ao registrar arquivo no banco após upload. conteudoId={} storagePath={}", new Object[]{idConteudo, storagePath}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }




            // delete arquivo sem cadastro tenta apagar o arquivo enviado quando o cadastro no banco falha
            try {

                storageService.excluir(
                        storagePath
                );

                logger.info("Arquivo removido do Storage após falha no banco. conteudoId={} storagePath={}", idConteudo, storagePath);
                // registro de informacoes confere se esta habilitado ao salvar arquivo completo
                if (logger.isInfoEnabled()) {
                    // gravar historico ao salvar arquivo completo tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "salvar", null,
                                MessageFormatter.arrayFormat("Arquivo removido do Storage após falha no banco. conteudoId={} storagePath={}", new Object[]{idConteudo, storagePath}).getMessage(), null, null);
                    } catch (Exception erroLogBD) {
                        logger.error("Erro ao gravar log no banco.", erroLogBD);
                    }
                }
            } catch (Exception erroRemocao) {
                logger.error("Erro ao remover arquivo do Storage após falha no banco. conteudoId={} storagePath={}", idConteudo, storagePath, erroRemocao);
                // registro de erros confere se esta habilitado ao salvar arquivo completo
                if (logger.isErrorEnabled()) {
                    // gravar historico ao salvar arquivo completo tenta salvar o registro no banco e informa falhas
                    try {
                        StringWriter excecaoLogBD = new StringWriter();
                        erroRemocao.printStackTrace(new PrintWriter(excecaoLogBD));
                        UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "salvar", null,
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

    // listar arquivos pelo servico este metodo consulta os registros e apresenta falhas de leitura
    public List<ArquivoDTO> listarTodos() {

        // listar arquivos consulta os registros e avisa quando a busca falha
        try {

            return arquivoRepository.listarTodos();

        } catch (SQLException e) {

            logger.error("Erro ao listar arquivos no banco.", e);
            // registro de erros confere se esta habilitado ao listar arquivos pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao listar arquivos pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "listarTodos", null,
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

    // buscar arquivo obrigatorio este metodo consulta o registro e recusa um identificador inexistente
    public ArquivoDTO buscarPorId(Long id) {

        // buscar arquivo por codigo consulta o arquivo e trata falhas no banco
        try {

            ArquivoDTO arquivo =
                    arquivoRepository.buscarPorId(id);

            // conferir arquivo encontrado avisa quando o codigo nao identifica um arquivo
            if (arquivo == null) {

                logger.warn("Arquivo não encontrado. arquivoId={}", id);
                // registro de avisos confere se esta habilitado ao buscar arquivo obrigatorio
                if (logger.isWarnEnabled()) {
                    // gravar historico ao buscar arquivo obrigatorio tenta salvar o registro no banco e informa falhas
                    try {
                        UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "buscarPorId", null,
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
            // registro de erros confere se esta habilitado ao buscar arquivo obrigatorio
            if (logger.isErrorEnabled()) {
                // gravar historico ao buscar arquivo obrigatorio tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "buscarPorId", null,
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
    // buscar anexo recente pelo servico este metodo consulta o ultimo arquivo ligado ao conteudo
    public ArquivoDTO buscarMaisRecentePorConteudo(Long idConteudo) {

        // buscar arquivo recente consulta o ultimo arquivo do conteudo e trata falhas no banco
        try {

            return arquivoRepository.buscarMaisRecentePorConteudo(idConteudo);

        } catch (SQLException e) {

            logger.error("Erro ao buscar arquivo do conteúdo. conteudoId={}", idConteudo, e);
            // registro de erros confere se esta habilitado ao buscar anexo recente pelo servico
            if (logger.isErrorEnabled()) {
                // gravar historico ao buscar anexo recente pelo servico tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "buscarMaisRecentePorConteudo", null,
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

    // baixar arquivo armazenado este metodo localiza o registro e recupera os dados do material
    public byte[] download(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);

        // baixar arquivo tenta obter os dados do armazenamento e registra o resultado
        try {
            byte[] dados = storageService.download(arquivo.getStoragePath());
            logger.debug("Download de arquivo concluído no servidor. arquivoId={} conteudoId={} tamanhoBytes={}", id, arquivo.getIdConteudo(), dados.length);
            // registro de detalhes confere se esta habilitado ao baixar arquivo armazenado
            if (logger.isDebugEnabled()) {
                // gravar historico ao baixar arquivo armazenado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "DEBUG", ArquivoService.class.getName(), "download", null,
                            MessageFormatter.arrayFormat("Download de arquivo concluído no servidor. arquivoId={} conteudoId={} tamanhoBytes={}", new Object[]{id, arquivo.getIdConteudo(), dados.length}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            return dados;
        } catch (RuntimeException e) {
            logger.error("Erro ao baixar arquivo do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            // registro de erros confere se esta habilitado ao baixar arquivo armazenado
            if (logger.isErrorEnabled()) {
                // gravar historico ao baixar arquivo armazenado tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "download", null,
                            MessageFormatter.arrayFormat("Erro ao baixar arquivo do Storage. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }
    }

    // delete arquivo completo este metodo remove o arquivo armazenado antes de apagar seu registro no banco
    public void excluir(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);



        // delete arquivo armazenado tenta apagar o arquivo e registra falhas
        try {
            storageService.excluir(arquivo.getStoragePath());
        } catch (RuntimeException e) {
            logger.error("Erro ao excluir arquivo do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            // registro de erros confere se esta habilitado ao delete arquivo completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Erro ao excluir arquivo do Storage. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, excecaoLogBD.toString());
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw e;
        }



        // delete registro do arquivo tenta apagar o cadastro depois da exclusao no armazenamento
        try {

            arquivoRepository.excluir(id);
            logger.info("Arquivo excluído do Storage e do banco. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo());
            // registro de informacoes confere se esta habilitado ao delete arquivo completo
            if (logger.isInfoEnabled()) {
                // gravar historico ao delete arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "INFO", ArquivoService.class.getName(), "excluir", null,
                            MessageFormatter.arrayFormat("Arquivo excluído do Storage e do banco. arquivoId={} conteudoId={}", new Object[]{id, arquivo.getIdConteudo()}).getMessage(), null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }

        } catch (SQLException e) {

            logger.error("Erro ao excluir registro do banco após remoção do Storage. arquivoId={} conteudoId={}", id, arquivo.getIdConteudo(), e);
            // registro de erros confere se esta habilitado ao delete arquivo completo
            if (logger.isErrorEnabled()) {
                // gravar historico ao delete arquivo completo tenta salvar o registro no banco e informa falhas
                try {
                    StringWriter excecaoLogBD = new StringWriter();
                    e.printStackTrace(new PrintWriter(excecaoLogBD));
                    UsuarioRepository.InserirLogsNoBD(null, "ERROR", ArquivoService.class.getName(), "excluir", null,
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

    // validar arquivo enviado este metodo confere presenca tamanho extensao e tipo permitido
    private void validarArquivo(
            MultipartFile arquivo) {

        // validar arquivo enviado rejeita arquivo ausente ou vazio
        if (arquivo == null
                || arquivo.isEmpty()) {

            logger.warn("Upload recusado: arquivo ausente ou vazio.");
            // registro de avisos confere se esta habilitado ao validar arquivo enviado
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar arquivo enviado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "validarArquivo", null,
                            "Upload recusado: arquivo ausente ou vazio.", null, null);
                } catch (Exception erroLogBD) {
                    logger.error("Erro ao gravar log no banco.", erroLogBD);
                }
            }
            throw new IllegalArgumentException(
                    "Selecione um arquivo."
            );
        }

        // validar tamanho do arquivo rejeita arquivos maiores que cinquenta megabytes
        if (arquivo.getSize()
                > TAMANHO_MAXIMO) {

            throw new IllegalArgumentException(
                    "O arquivo deve possuir no máximo 50 MB."
            );
        }

        String extensao = extrairExtensao(arquivo.getOriginalFilename());

        // validar extensao do arquivo rejeita nomes sem extensao permitida
        if (extensao == null
                || !EXTENSOES_PERMITIDAS.contains(extensao)) {

            throw new IllegalArgumentException(
                    "Extensão de arquivo não permitida. Formatos aceitos: "
                            + "PDF, PNG, JPG, JPEG, DOC, DOCX, PPT e PPTX."
            );
        }

        String mimeType =
                arquivo.getContentType();

        // validar tipo do arquivo rejeita tipos de arquivo nao permitidos
        if (mimeType == null
                || !TIPOS_PERMITIDOS.contains(mimeType)) {

            logger.warn("Upload recusado: tipo de arquivo não permitido. mimeType={}", mimeType);
            // registro de avisos confere se esta habilitado ao validar arquivo enviado
            if (logger.isWarnEnabled()) {
                // gravar historico ao validar arquivo enviado tenta salvar o registro no banco e informa falhas
                try {
                    UsuarioRepository.InserirLogsNoBD(null, "WARN", ArquivoService.class.getName(), "validarArquivo", null,
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

    // identificar extensao do arquivo este metodo le o trecho depois do ultimo ponto em minusculas
    private String extrairExtensao(String nomeArquivo) {

        // extrair extensao ignora arquivo sem nome
        if (nomeArquivo == null) {
            return null;
        }

        int ponto = nomeArquivo.lastIndexOf('.');

        // conferir extensao retorna sem extensao quando falta o ponto ou o texto depois dele
        if (ponto < 0 || ponto == nomeArquivo.length() - 1) {
            return null;
        }

        return nomeArquivo.substring(ponto + 1).toLowerCase();
    }
}