// verificacao do banco dos paineis confere a identificacao do usuario os filtros e o envio dos parametros das consultas
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DashboardFiltro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco dos paineis esta classe usa o banco simulado com consultas de parametros nomeados
class DashboardRepositoryTests {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 12, 0);

    private BancoSimulado banco;
    private DashboardRepository painel;

    // preparar testes dos paineis este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        painel = new DashboardRepository(banco.jdbcTemplate());
    }

    // testar identificacao do usuario este teste confere a tabela usada para cada perfil e o perfil invalido
    @Test
    void identificaUsuarioPeloPerfil() {
        // preparar professor encontrado
        banco.quandoConsultar("SELECT p.id FROM public.\"PROFESSORES\"", linha("id", 4L));
        banco.semResultado("SELECT p.id FROM public.\"ALUNOS\"");

        // executar identificacoes
        Long professor = painel.buscarIdentidade("p@teste.com", "professor");
        Long aluno = painel.buscarIdentidade("x@teste.com", "aluno");
        painel.buscarIdentidade("adm@teste.com", "admin");

        // verificar codigo perfil enviado e recusa de perfil invalido
        assertEquals(4L, professor);
        assertNull(aluno);
        assertTrue(banco.comandos().get(0).valores().contains("PROFESSORES"));
        assertTrue(banco.comandos().get(2).sql().contains("ADMINISTRADOR"));
        assertThrows(IllegalArgumentException.class, () -> painel.buscarIdentidade("x@teste.com", "visitante"));
    }

    // testar filtros do painel este teste confere o inicio do periodo o fim no dia seguinte e a semana seguinte
    @Test
    void montaParametrosDoPeriodo() {
        // preparar filtro dos ultimos sete dias
        DashboardFiltro filtro = new DashboardFiltro("7", 2, "ALTA", "invalido", AGORA);

        // executar montagem
        MapSqlParameterSource parametros = painel.parametros("professor", 4L, filtro);

        // verificar valores dos filtros
        assertEquals(LocalDate.of(2026, 11, 4), parametros.getValue("inicio"));
        assertEquals(LocalDate.of(2026, 11, 11), parametros.getValue("fim"));
        assertEquals(AGORA.plusDays(7), parametros.getValue("semana"));
        assertEquals("todos", parametros.getValue("perfil"));
        assertEquals(2L, parametros.getValue("turma"));
    }

    // testar consultas do painel este teste confere que cada indicador consulta o banco com os filtros informados
    @Test
    void consultaIndicadoresDeTodosOsPerfis() {
        // preparar filtro de todo o periodo
        MapSqlParameterSource parametros = painel.parametros("admin", 1L, new DashboardFiltro("todos", 0, "todos", "todos", AGORA));

        // executar consultas de aluno professor e administrador
        List<Object> resultados = List.of(
                painel.listarTurmas(parametros), painel.resumoAluno(parametros), painel.evolucaoAluno(parametros),
                painel.turmasAluno(parametros), painel.resumoEventos(parametros), painel.eventosPorTurma(parametros),
                painel.prioridades(parametros), painel.proximosEventos(parametros), painel.resumoConteudos(parametros),
                painel.situacaoConteudos(parametros), painel.conteudosPorTurma(parametros),
                painel.listarConteudos(parametros, true), painel.resumoTurmasProfessor(parametros),
                painel.turmasProfessor(parametros), painel.eventosPorMes(parametros), painel.resumoUsuarios(parametros),
                painel.usuariosPorMes(parametros), painel.usuariosRecentes(parametros), painel.resumoDenuncias(parametros),
                painel.denunciasPorSituacao(parametros), painel.resumoTurmasAdmin(parametros), painel.turmasAdmin(parametros));

        // verificar uma consulta por indicador e o filtro de atencao enviado
        assertEquals(22, banco.comandos().size());
        assertEquals(Map.of("id", 1L), resultados.get(1));
        assertTrue(banco.ultimo("Professor não disponível").valores().contains(true));
    }
}
