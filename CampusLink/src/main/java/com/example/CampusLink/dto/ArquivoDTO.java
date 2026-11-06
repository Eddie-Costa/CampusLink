// dados de arquivo organiza as informacoes usadas entre banco armazenamento e telas
package com.example.CampusLink.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

// registro do material enviado esta classe guarda caminho nome tipo tamanho autor e conteudo associado
@Getter
@Setter
public class ArquivoDTO {

    private Long id;

    private Long idConteudo;

    private OffsetDateTime createdAt;

    private String bucketId;

    private String storagePath;

    private String nomeOriginal;

    private String mimeType;

    private Long tamanhoBytes;

    private UUID uploadedBy;
}
