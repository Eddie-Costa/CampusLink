// versao dos termos de uso transporta o texto publicado e seu hash sha-256
package com.example.CampusLink.dto;

import java.time.OffsetDateTime;

// dados da versao dos termos este registro guarda numero texto hash e data de publicacao
public record TermoVersaoDTO(Long id, String versao, String conteudo, String hashSha256, OffsetDateTime publicadoEm) {
}
