// filtros dos dashboards define as opcoes de recorte dos indicadores
package com.example.CampusLink.dto;

import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// recorte do painel esta classe valida periodo turma prioridade e perfil e calcula a data inicial
@Getter
public class DashboardFiltro {
    private final String periodo;
    private final long turma;
    private final String prioridade;
    private final String perfil;
    private final LocalDateTime agora;
    private final LocalDate inicio;

    // preparar filtros atuais este construtor usa a data e a hora de sao paulo para montar o recorte
    public DashboardFiltro(String periodo, long turma, String prioridade, String perfil) {
        this(periodo, turma, prioridade, perfil, LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
    }

    // validar filtros do dashboard este construtor normaliza as opcoes e calcula o inicio do periodo
    public DashboardFiltro(String periodo, long turma, String prioridade, String perfil, LocalDateTime agora) {
        this.periodo = List.of("todos", "7", "30", "mes").contains(periodo) ? periodo : "todos";
        this.turma = Math.max(0, turma);
        this.prioridade = List.of("ALTA", "MEDIA", "BAIXA").contains(prioridade) ? prioridade : "todos";
        this.perfil = List.of("ALUNOS", "PROFESSORES", "ADMINISTRADOR").contains(perfil) ? perfil : "todos";
        this.agora = agora;
        this.inicio = switch (this.periodo) {
            case "7" -> agora.toLocalDate().minusDays(6);
            case "30" -> agora.toLocalDate().minusDays(29);
            case "mes" -> agora.toLocalDate().withDayOfMonth(1);
            default -> null;
        };
    }
}
