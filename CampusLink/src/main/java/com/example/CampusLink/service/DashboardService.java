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

    public Map<String, Object> professor(long id, DashboardFiltro filtro) {
        var p = repository.parametros("professor", id, filtro);
        var turmas = repository.resumoTurmasProfessor(p);
        var conteudos = repository.resumoConteudos(p);
        var eventos = repository.resumoEventos(p);
        var porTurma = repository.conteudosPorTurma(p);
        List<Map<String, Object>> cards = List.of(
                card("Turmas sob responsabilidade", turmas.get("gerenciadas"), "Você é o professor responsável"),
                card("Turmas participantes", turmas.get("participantes"), "Você participa, sem ser responsável"),
                card("Alunos nas turmas", turmas.get("alunos"), "Alunos distintos no recorte de turmas"),
                card("Conteúdos publicados", conteudos.get("conteudos"), "Sua autoria, incluindo removidos"),
                card("Conteúdos ativos", conteudos.get("ativos"), "Disponíveis para os alunos"),
                card("Suspensos por denúncias", conteudos.get("suspensos"), "Inclui os que aguardam análise"),
                card("Aguardando análise", conteudos.get("analise"), "Suspensos com revisão solicitada"),
                card("Eventos criados", eventos.get("eventos"), "Eventos ativos de sua autoria nas turmas"),
                card("Eventos futuros", eventos.get("futuros"), "Ainda não iniciados"),
                card("Eventos passados", eventos.get("passados"), "Data final já ultrapassada"));
        var graficos = List.of(
                grafico("Conteúdos por turma", "bar", "Suas publicações; até 10 turmas. Materiais compartilhados aparecem em cada turma.", porTurma),
                grafico("Situação dos conteúdos", "doughnut", "Categorias separadas; em análise significa revisão solicitada.", repository.situacaoConteudos(p)),
                grafico("Eventos criados por mês", "line", "Data de cadastro dos eventos ativos de sua autoria.", repository.eventosPorMes(p)),
                grafico("Distribuição dos eventos", "doughnut", "Situação temporal dos eventos no recorte.", distribuicaoEventos(eventos)));
        List<String> insights = new ArrayList<>();
        insights.add("Você é responsável por " + turmas.get("gerenciadas") + " turmas e participa de outras " + turmas.get("participantes") + " no recorte.");
        insights.add("Há " + eventos.get("proximos") + " eventos de sua autoria nos próximos 7 dias, conforme os filtros.");
        insights.add("Você possui " + conteudos.get("suspensos") + " conteúdos suspensos no recorte; " + conteudos.get("analise") + " aguardam análise administrativa.");
        if (!porTurma.isEmpty() && numero(porTurma.getFirst(), "quantidade") > 0) {
            insights.add("A turma com mais publicações suas no recorte é " + porTurma.getFirst().get("nome") + ".");
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("cards", cards); dados.put("graficos", graficos); dados.put("insights", insights);
        dados.put("turmas", repository.turmasProfessor(p));
        dados.put("conteudos", repository.listarConteudos(p, false));
        dados.put("eventos", repository.proximosEventos(p));
        return dados;
    }

    private List<Map<String, Object>> distribuicaoEventos(Map<String, Object> eventos) {
        return List.of(ponto("Futuros", eventos.get("futuros")), ponto("Em andamento", eventos.get("andamento")),
                ponto("Passados", eventos.get("passados")));
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
