// aceite dos termos de uso transporta a versao aceita o hash e a data exibidos no perfil
package com.example.CampusLink.dto;

import java.time.OffsetDateTime;

// dados do aceite ativo este registro guarda a versao o hash sha-256 e o momento do aceite
public record TermoAceiteDTO(String versao, String hashSha256, OffsetDateTime aceitoEm) {
}
