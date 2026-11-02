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

    public Map<String, Object> admin(long id, DashboardFiltro filtro) {
        var p = repository.parametros("admin", id, filtro);
        var usuarios = repository.resumoUsuarios(p);
        var conteudos = repository.resumoConteudos(p);
        var turmas = repository.resumoTurmasAdmin(p);
        var eventos = repository.resumoEventos(p);
        var denuncias = repository.resumoDenuncias(p);
        var porTurma = repository.conteudosPorTurma(p);
        List<Map<String, Object>> grupos = List.of(
                grupo("Contas", List.of(
                        card("Contas cadastradas", usuarios.get("usuarios"), "Cadastros no período e perfil selecionados"),
                        card("Alunos", usuarios.get("alunos"), "Contas com perfil de aluno"),
                        card("Professores", usuarios.get("professores"), "Contas com perfil de professor"),
                        card("Administradores", usuarios.get("administradores"), "Contas do backoffice"),
                        card("Contas ativas", usuarios.get("ativos"), "Situação atual das contas do recorte"),
                        card("Contas inativas", usuarios.get("inativos"), "Não representam bloqueio ou suspensão"))),
                grupo("Conteúdos", List.of(
                        card("Conteúdos publicados", conteudos.get("conteudos"), "Publicações no período, incluindo removidos"),
                        card("Conteúdos ativos", conteudos.get("ativos"), "Disponíveis para consulta"),
                        card("Suspensos por denúncias", conteudos.get("suspensos"), "Inclui os que aguardam análise"),
                        card("Aguardando análise", conteudos.get("analise"), "Suspensos com revisão solicitada"))),
                grupo("Turmas e eventos", List.of(
                        card("Turmas cadastradas", turmas.get("turmas"), "Criadas no período selecionado"),
                        card("Professores responsáveis", turmas.get("responsaveis"), "Responsáveis distintos nas turmas do recorte"),
                        card("Eventos cadastrados", turmas.get("eventos"), "Todos os status, criados no período"),
                        card("Eventos futuros", eventos.get("futuros"), "Eventos ativos criados no período"),
                        card("Eventos passados", eventos.get("passados"), "Eventos ativos com data final ultrapassada"))),
                grupo("Denúncias", List.of(
                        card("Denúncias registradas", denuncias.get("denuncias"), "Recebidas no período selecionado"),
                        card("Conteúdos denunciados", denuncias.get("denunciados"), "Conteúdos distintos nas denúncias do recorte"),
                        card("Denúncias pendentes", denuncias.get("pendentes"), "Ainda sem decisão registrada"),
                        card("Denúncias analisadas", denuncias.get("resolvidas"), "Com liberação ou remoção registrada"))));
        var graficos = List.of(
                grafico("Usuários por perfil", "doughnut", "Contas conforme os filtros de período e perfil.", List.of(
                        ponto("Alunos", usuarios.get("alunos")), ponto("Professores", usuarios.get("professores")), ponto("Administradores", usuarios.get("administradores")))),
                grafico("Situação das contas", "doughnut", "Estado atual das contas cadastradas no recorte.", List.of(
                        ponto("Ativas", usuarios.get("ativos")), ponto("Inativas", usuarios.get("inativos")), ponto("Não informado", usuarios.get("sem_status")))),
                grafico("Situação dos conteúdos", "doughnut", "Categorias sem sobreposição; inclui removidos.", repository.situacaoConteudos(p)),
                grafico("Cadastros ao longo do tempo", "line", "Novas contas por mês, conforme o perfil selecionado.", repository.usuariosPorMes(p)),
                grafico("Situação das denúncias", "bar", "Situação atual das denúncias recebidas no período.", repository.denunciasPorSituacao(p)),
                grafico("Conteúdos por turma", "bar", "Até 10 turmas atuais com mais publicações no período; inclui removidos.", porTurma));
        List<String> insights = new ArrayList<>();
        insights.add("Existem " + conteudos.get("analise") + " conteúdos publicados no recorte aguardando análise administrativa.");
        insights.add("Há " + usuarios.get("inativos") + " contas inativas entre os cadastros selecionados.");
        insights.add("Foram registradas " + denuncias.get("denuncias") + " denúncias no período; " + denuncias.get("pendentes") + " continuam pendentes.");
        insights.add("Há registro de liberação em " + denuncias.get("liberados") + " conteúdos distintos nas denúncias do recorte. Isso não indica sua situação atual.");
        if (!porTurma.isEmpty() && numero(porTurma.getFirst(),"quantidade") > 0) {
            insights.add("A turma com mais publicações no período é " + porTurma.getFirst().get("nome") + ".");
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("grupos",grupos); dados.put("graficos",graficos); dados.put("insights",insights);
        dados.put("usuarios",repository.usuariosRecentes(p));
        dados.put("conteudos",repository.listarConteudos(p,true));
        dados.put("turmas",repository.turmasAdmin(p));
        return dados;
    }

    private Map<String, Object> grupo(String titulo, List<Map<String, Object>> cards) {
        return Map.of("titulo",titulo,"cards",cards);
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
