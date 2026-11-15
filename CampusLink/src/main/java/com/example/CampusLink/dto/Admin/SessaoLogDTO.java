package com.example.CampusLink.dto.Admin;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

// dados de consulta do historico sem alterar a sessao armazenada
@Getter
@Setter
public class SessaoLogDTO {
    private UUID sessaoLogId;
    private Long usuarioId;
    private String nomeUsuario;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private String logCompleto;

    // calcular duracao este metodo mostra o tempo da sessao em horas minutos e segundos
    public String getDuracao() {
        // avisar duracao desconhecida quando falta inicio ou fim ou o fim veio antes do inicio
        if (inicio == null || fim == null || fim.isBefore(inicio)) return "Não disponível";
        long segundos = Duration.between(inicio, fim).getSeconds();
        return String.format("%dh %02dmin %02ds", segundos / 3600, (segundos % 3600) / 60, segundos % 60);
    }
}
