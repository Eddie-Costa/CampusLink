package com.example.CampusLink.service;

import com.example.CampusLink.dao.ArquivoDAO;
import com.example.CampusLink.dto.ArquivoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Service
public class ArquivoService {

    private final ArquivoDAO arquivoDAO;
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
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            throw new IllegalArgumentException(
                    "Não foi possível enviar o arquivo. Verifique o tamanho e tente novamente."
            );
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

        } catch (SQLException e) {

            // Storage funcionou, mas banco falhou.
            // Tentamos remover o arquivo que foi enviado.

            try {

                storageService.excluir(
                        storagePath
                );

            } catch (Exception ignored) {
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

                throw new IllegalArgumentException(
                        "Arquivo não encontrado."
                );
            }

            return arquivo;

        } catch (SQLException e) {

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

            throw new RuntimeException(
                    "Erro ao buscar o arquivo do conteúdo.",
                    e
            );
        }
    }

    public byte[] download(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);

        return storageService.download(
                arquivo.getStoragePath()
        );
    }

    public void excluir(Long id) {

        ArquivoDTO arquivo =
                buscarPorId(id);

        // Primeiro removemos o arquivo físico.

        storageService.excluir(
                arquivo.getStoragePath()
        );

        // Depois removemos o registro do banco.

        try {

            arquivoDAO.excluir(id);

        } catch (SQLException e) {

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

            throw new IllegalArgumentException(
                    "Selecione um arquivo."
            );
        }

        if (arquivo.getSize()
                > TAMANHO_MAXIMO) {

            throw new IllegalArgumentException(
                    "O arquivo deve possuir no máximo 50 MB."
            );
        }

        String extensao = extrairExtensao(arquivo.getOriginalFilename());

        if (extensao == null
                || !EXTENSOES_PERMITIDAS.contains(extensao)) {

            throw new IllegalArgumentException(
                    "Extensão de arquivo não permitida. Formatos aceitos: "
                            + "PDF, PNG, JPG, JPEG, DOC, DOCX, PPT e PPTX."
            );
        }

        String mimeType =
                arquivo.getContentType();

        if (mimeType == null
                || !TIPOS_PERMITIDOS.contains(mimeType)) {

            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido."
            );
        }
    }

    private String extrairExtensao(String nomeArquivo) {

        if (nomeArquivo == null) {
            return null;
        }

        int ponto = nomeArquivo.lastIndexOf('.');

        if (ponto < 0 || ponto == nomeArquivo.length() - 1) {
            return null;
        }

        return nomeArquivo.substring(ponto + 1).toLowerCase();
    }
}