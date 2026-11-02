package com.example.CampusLink.dao;

import com.example.CampusLink.controller.Geral.DashboardController;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.DashboardFiltro;
import com.example.CampusLink.repository.DashboardRepository;
import com.example.CampusLink.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class DashboardRepositoryTests {
    @Autowired DataSource dataSource;
    @Autowired ThymeleafViewResolver viewResolver;
    private Connection conn;
    private DashboardRepository repository;
    private final LocalDateTime agora = LocalDateTime.of(2026, 11, 2, 12, 0);

    @BeforeEach
    void preparar() throws Exception {
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        for (String tabela : new String[]{"USUARIOS", "ALUNOS", "PROFESSORES", "ADMINISTRADOR", "TURMAS",
                "CONTEUDOS", "EVENTOS", "EVENTO_CONTEUDO", "PROGRESSO_CONTEUDO", "DENUNCIAS"}) {
            sql("CREATE TEMP TABLE \"" + tabela + "\" (LIKE public.\"" + tabela + "\" INCLUDING CONSTRAINTS)");
        }
        // consultas e dados de teste ficam na mesma conexão, apenas em tabelas temporárias
        Connection isolada = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("prepareStatement")) args[0] = ((String) args[0]).replace("public.", "pg_temp.");
                    try { return method.invoke(conn, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        DataSource temporario = mock(DataSource.class);
        when(temporario.getConnection()).thenReturn(isolada);
        repository = new DashboardRepository(new JdbcTemplate(temporario));
        sql("""
                INSERT INTO pg_temp."USUARIOS" (id,nome,email,telefone,datanasc,senha,perfil,status,created_at)
                VALUES (1,'Aluno','aluno@example.com','11999990001','2000-01-01','hash','ALUNOS',true,'2026-11-01');
                INSERT INTO pg_temp."ALUNOS" (id,id_usuario,rgm,created_at) VALUES (10,1,'12345678901',now());
                INSERT INTO pg_temp."TURMAS" (id,nome_turma,id_alunos,id_professores,id_proprietario,created_at)
                VALUES (100,'Turma A',ARRAY[10]::bigint[],ARRAY[20]::bigint[],20,now()),
                       (200,'Turma B',ARRAY[10]::bigint[],ARRAY[20]::bigint[],30,now()),
                       (300,'Turma privada',ARRAY[11]::bigint[],ARRAY[30]::bigint[],30,now());
                INSERT INTO pg_temp."CONTEUDOS" (id,id_turma,id_professor,created_at,titulo,descricao,tipo,duracao_estimada,prioridade,status,revisao_solicitada)
                VALUES (1,ARRAY[100,200]::bigint[],20,now(),'Compartilhado','Texto','texto','30','baixa','ativo',false),
                       (2,ARRAY[100]::bigint[],20,now(),'Pendente','Texto','texto','30','baixa','ativo',false),
                       (3,ARRAY[300]::bigint[],30,now(),'Privado','Texto','texto','30','baixa','ativo',false),
                       (4,ARRAY[100]::bigint[],20,now(),'Suspenso','Texto','texto','30','baixa','suspenso_denuncia',true);
                INSERT INTO pg_temp."PROGRESSO_CONTEUDO" (id,id_aluno,id_conteudo,concluido_em)
                VALUES (1,10,1,'2026-11-01 12:00:00-03'),(2,11,2,'2026-11-01 12:00:00-03');
                INSERT INTO pg_temp."EVENTOS" (id,id_turma,id_professor,titulo,tipo,descricao,data_hora,data_fim,status,created_at,prioridade)
                VALUES (1,100,20,'Próximo','prova','Teste','2026-11-03 12:00','2026-11-03 16:00-03','ATIVO',now(),'ALTA'),
                       (2,100,20,'Passado','prova','Teste','2026-10-31 12:00','2026-10-31 16:00-03','ATIVO',now(),'BAIXA'),
                       (3,300,30,'Privado','prova','Teste','2026-11-03 12:00',null,'ATIVO',now(),'ALTA');
                INSERT INTO pg_temp."EVENTO_CONTEUDO" (id_evecon,id_evento,id_conteudo) VALUES (1,2,2);
                """);
    }

    @AfterEach
    void limpar() throws Exception {
        if (conn != null) {
            try { sql("DISCARD TEMP"); } finally { conn.close(); }
        }
    }

    @Test
    void contaSemDuplicarConteudoCompartilhadoESemVazarDados() {
        var p = repository.parametros("aluno", 10, filtro("todos",0,"todos"));
        var resumo = repository.resumoAluno(p);
        assertEquals(2L, resumo.get("turmas"));
        assertEquals(2L, resumo.get("conteudos"));
        assertEquals(1L, resumo.get("concluidos"));
        assertEquals(1L, resumo.get("atrasados"));
        assertEquals(0L, resumo.get("pendentes"));
        assertEquals(50, ((Number) resumo.get("progresso")).intValue());
        assertEquals(2L, repository.resumoEventos(p).get("eventos"));
        assertEquals(1L, repository.resumoEventos(p).get("proximos"));
        assertEquals(1, repository.proximosEventos(p).size());
        assertEquals(1L, repository.evolucaoAluno(p).getFirst().get("quantidade"));
        assertEquals(2, repository.eventosPorTurma(p).size());
        assertEquals(2, repository.prioridades(p).size());
        assertEquals(2, repository.turmasAluno(p).size());
        assertEquals(10L, repository.buscarIdentidade("ALUNO@example.com", "aluno"));
        assertNull(repository.buscarIdentidade("aluno@example.com", "admin"));
    }

    @Test
    void filtrosPeriodoPrioridadeTurmaEVazio() {
        var filtrado = repository.parametros("aluno",10,filtro("7",100,"ALTA"));
        assertEquals(0L, repository.resumoEventos(filtrado).get("eventos"));
        var turma = repository.parametros("aluno",10,filtro("todos",200,"todos"));
        assertEquals(1L, repository.resumoAluno(turma).get("conteudos"));
        var alheia = repository.parametros("aluno",10,filtro("todos",300,"todos"));
        assertEquals(0L, repository.resumoAluno(alheia).get("conteudos"));
        assertTrue(repository.proximosEventos(alheia).isEmpty());
        assertEquals(0, ((Number) repository.resumoAluno(alheia).get("progresso")).intValue());
    }

    @Test
    void reaberturaEEventoFuturoEvitamAtraso() throws Exception {
        sql("UPDATE pg_temp.\"CONTEUDOS\" SET reaberto_em = '2026-11-01 12:00-03' WHERE id=2");
        var p = repository.parametros("aluno",10,filtro("todos",0,"todos"));
        assertEquals(0L, repository.resumoAluno(p).get("atrasados"));
        sql("UPDATE pg_temp.\"CONTEUDOS\" SET reaberto_em = null WHERE id=2");
        sql("INSERT INTO pg_temp.\"EVENTO_CONTEUDO\" (id_evecon,id_evento,id_conteudo) VALUES (2,1,2)");
        assertEquals(0L, repository.resumoAluno(p).get("atrasados"));
    }

    @Test
    void renderizaPaginaComDadosEVazia() throws Exception {
        var resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(viewResolver.getTemplateEngine());
        resolver.setCharacterEncoding("UTF-8");
        var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(repository, new DashboardService(repository)))
                .setViewResolvers(resolver).build();
        var session = new MockHttpSession();
        var aluno = new loginAlunoDTO(); aluno.setEmail("aluno@example.com");
        session.setAttribute("usuarioLogado", aluno);
        session.setAttribute("tipoUsuario", "aluno"); session.setAttribute("email2FA", aluno.getEmail());
        String html = mvc.perform(get("/dashboard/aluno").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Insights dos Estudos"));
        assertTrue(html.contains("Turma A"));
        assertFalse(html.contains("Turma privada"));
        sql("UPDATE pg_temp.\"TURMAS\" SET id_alunos=ARRAY[]::bigint[]");
        mvc.perform(get("/dashboard/aluno").session(session)).andExpect(status().isOk());
    }

    private DashboardFiltro filtro(String periodo, long turma, String prioridade) {
        return new DashboardFiltro(periodo,turma,prioridade,"todos",agora);
    }

    private void sql(String sql) throws Exception {
        try (var stmt = conn.createStatement()) { stmt.execute(sql); }
    }
}
