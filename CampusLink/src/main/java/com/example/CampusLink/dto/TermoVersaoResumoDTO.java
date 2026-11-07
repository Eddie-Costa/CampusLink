// historico dos termos de uso transporta cada versao publicada para a tela do admin master
package com.example.CampusLink.dto;

import java.time.OffsetDateTime;

// dados do historico dos termos este registro guarda a versao o hash a data e a quantidade de aceites ativos
public record TermoVersaoResumoDTO(Long id, String versao, String conteudo, String hashSha256,
                                   OffsetDateTime publicadoEm, long aceites) {
}
