package com.example.CampusLink.dto;

import java.time.OffsetDateTime;

public record TermoVersaoResumoDTO(Long id, String versao, String conteudo, String hashSha256,
                                   OffsetDateTime publicadoEm, long aceites) {
}
