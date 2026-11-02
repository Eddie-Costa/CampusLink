package com.example.CampusLink.service;

import com.example.CampusLink.dto.DashboardFiltro;
import com.example.CampusLink.repository.DashboardRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> aluno(long id, DashboardFiltro filtro) {
        var p = repository.parametros("aluno", id, filtro);
        var resumo = repository.resumoAluno(p);
        var eventos = repository.resumoEventos(p);
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("Minhas turmas", resumo.get("turmas"), "Turmas do recorte selecionado"));
        cards.add(card("Conteúdos disponíveis", resumo.get("conteudos"), "Materiais ativos nas suas turmas"));
        cards.add(card("Conteúdos concluídos", resumo.get("concluidos"), "Conclusões marcadas por você"));
        cards.add(card("Progresso dos conteúdos", resumo.get("progresso") + "%", "Concluídos ÷ conteúdos disponíveis"));
        cards.add(card("Conteúdos pendentes", resumo.get("pendentes"), "Ainda não concluídos e sem atraso"));
        cards.add(card("Conteúdos atrasados", resumo.get("atrasados"), "Prazo encerrado, sem conclusão ou reabertura"));
        cards.add(card("Eventos no recorte", eventos.get("eventos"), "Eventos ativos conforme os filtros"));
        cards.add(card("Próximos 7 dias", eventos.get("proximos"), "Eventos do recorte, a partir de agora"));

        List<Map<String, Object>> graficos = List.of(
                grafico("Situação dos conteúdos", "doughnut", "Conclusão de materiais, sem inferir conclusão de eventos.",
                        List.of(ponto("Pendentes", resumo.get("pendentes")), ponto("Concluídos", resumo.get("concluidos")),
                                ponto("Atrasados", resumo.get("atrasados")))),
                grafico("Evolução dos estudos", "line", "Conteúdos concluídos por mês no período selecionado.", repository.evolucaoAluno(p)),
                grafico("Eventos por turma", "bar", "Até 10 turmas com mais eventos no recorte.", repository.eventosPorTurma(p)),
                grafico("Prioridades dos eventos", "doughnut", "Prioridades registradas nos eventos do recorte.", repository.prioridades(p)));

        List<String> insights = new ArrayList<>();
        insights.add("Você possui " + eventos.get("proximos") + " eventos do recorte nos próximos 7 dias.");
        insights.add("Há " + eventos.get("alta") + " eventos de prioridade alta que ainda não passaram, conforme os filtros.");
        insights.add("Você concluiu " + resumo.get("concluidos") + " de " + resumo.get("conteudos") + " conteúdos disponíveis nas turmas selecionadas.");
        if (numero(resumo, "atrasados") > 0) {
            insights.add("Revise os " + resumo.get("atrasados") + " conteúdos atrasados no Score para reorganizar seus estudos.");
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("cards", cards);
        dados.put("graficos", graficos);
        dados.put("insights", insights);
        dados.put("turmas", repository.turmasAluno(p));
        dados.put("eventos", repository.proximosEventos(p));
        return dados;
    }

    private Map<String, Object> card(String titulo, Object valor, String detalhe) {
        return Map.of("titulo", titulo, "valor", valor, "detalhe", detalhe);
    }

    private Map<String, Object> grafico(String titulo, String tipo, String detalhe, List<Map<String, Object>> pontos) {
        return Map.of("titulo", titulo, "tipo", tipo, "detalhe", detalhe, "pontos", pontos);
    }

    private Map<String, Object> ponto(String nome, Object quantidade) {
        return Map.of("nome", nome, "quantidade", quantidade);
    }

    private long numero(Map<String, Object> dados, String campo) {
        return ((Number) dados.get(campo)).longValue();
    }
}
