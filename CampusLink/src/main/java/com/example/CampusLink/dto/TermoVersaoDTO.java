package com.example.CampusLink.dto;

import java.time.OffsetDateTime;

public record TermoVersaoDTO(Long id, String versao, String conteudo, String hashSha256, OffsetDateTime publicadoEm) {
}
