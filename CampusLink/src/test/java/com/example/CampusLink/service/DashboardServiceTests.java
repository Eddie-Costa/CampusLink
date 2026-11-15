// verificacao dos paineis confere cards graficos e avisos montados para aluno professor e administrador
package com.example.CampusLink.service;

import com.example.CampusLink.dto.DashboardFiltro;
import com.example.CampusLink.repository.DashboardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// testes dos paineis esta classe simula o banco devolvendo o mesmo total para todos os indicadores
class DashboardServiceTests {

    private static final DashboardFiltro FILTRO = new DashboardFiltro("todos", 0, "todos", "todos", LocalDateTime.of(2026, 11, 10, 12, 0));

    // testar painel do aluno este teste confere cards progresso destaques e aviso de conteudos atrasados
    @Test
    @SuppressWarnings("unchecked")
    void montaPainelDoAlunoComAvisoDeAtraso() {
        // preparar banco com dois itens em cada indicador
        DashboardService painel = new DashboardService(banco(2L, "Calculo", 3L));

        // executar montagem
        Map<String, Object> dados = painel.aluno(7L, FILTRO);

        // verificar cards progresso destaques e avisos
        List<Map<String, Object>> cards = (List<Map<String, Object>>) dados.get("cards");
        assertEquals(8, cards.size());
        assertEquals("2%", cards.get(3).get("valor"));
        assertEquals(4, ((List<?>) dados.get("destaques")).size());
        List<String> avisos = (List<String>) dados.get("insights");
        assertTrue(avisos.get(avisos.size() - 1).contains("conteúdos atrasados"));
        assertEquals(4, ((List<?>) dados.get("graficos")).size());
    }

    // testar aluno em dia este teste confere que o aviso de atraso nao aparece sem conteudos atrasados
    @Test
    @SuppressWarnings("unchecked")
    void omiteAvisoDeAtrasoQuandoAlunoEstaEmDia() {
        // preparar banco sem nenhum indicador
        DashboardService painel = new DashboardService(banco(0L, "Calculo", 0L));

        // executar montagem
        List<String> avisos = (List<String>) painel.aluno(7L, FILTRO).get("insights");

        // verificar ausencia do aviso
        assertTrue(avisos.stream().noneMatch(aviso -> aviso.contains("atrasados no Score")));
    }

    // testar painel do professor este teste confere grupos destaque e a turma com mais publicacoes
    @Test
    @SuppressWarnings("unchecked")
    void montaPainelDoProfessorComTurmaDestaque() {
        // preparar banco com ranking de turmas
        DashboardService painel = new DashboardService(banco(2L, "Calculo", 3L));

        // executar montagem
        Map<String, Object> dados = painel.professor(4L, FILTRO);

        // verificar cards grupos e aviso da turma destaque
        assertEquals(10, ((List<?>) dados.get("cards")).size());
        assertEquals(3, ((List<?>) dados.get("grupos")).size());
        List<String> avisos = (List<String>) dados.get("insights");
        assertTrue(avisos.contains("A turma com mais publicações suas no recorte é Calculo."));
    }

    // testar painel do administrador este teste confere os quatro grupos e o grafico com escala minima
    @Test
    @SuppressWarnings("unchecked")
    void montaPainelDoAdministrador() {
        // preparar banco com ranking zerado
        DashboardService painel = new DashboardService(banco(5L, "Fisica", 0L));

        // executar montagem
        Map<String, Object> dados = painel.admin(1L, FILTRO);

        // verificar grupos destaques escala dos graficos e ausencia de turma destaque
        assertEquals(4, ((List<?>) dados.get("grupos")).size());
        assertEquals(4, ((List<?>) dados.get("destaques")).size());
        List<Map<String, Object>> graficos = (List<Map<String, Object>>) dados.get("graficos");
        assertEquals(6, graficos.size());
        assertEquals(1L, graficos.get(5).get("maximo"));
        List<String> avisos = (List<String>) dados.get("insights");
        assertFalse(avisos.stream().anyMatch(aviso -> aviso.contains("Fisica")));
    }

    // montar banco simulado este metodo devolve o mesmo total em resumos e uma turma em todas as listas
    private DashboardRepository banco(long total, String turma, long quantidade) {
        Map<String, Object> resumo = new HashMap<>() {
            @Override
            public Object get(Object chave) {
                return total;
            }
        };
        List<Map<String, Object>> lista = List.of(Map.of("nome", turma, "quantidade", quantidade));
        return mock(DashboardRepository.class, chamada -> {
            Class<?> tipo = chamada.getMethod().getReturnType();
            if (tipo == Map.class) return resumo;
            if (tipo == List.class) return lista;
            if (tipo == MapSqlParameterSource.class) return new MapSqlParameterSource();
            return null;
        });
    }
}
