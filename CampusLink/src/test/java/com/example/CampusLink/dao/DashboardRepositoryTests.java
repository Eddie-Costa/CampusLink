package com.example.CampusLink.dao;

import com.example.CampusLink.controller.Geral.DashboardController;
import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.dto.Admin.loginAdminDTO;
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
                INSERT INTO pg_temp."ALUNOS" (id,id_usuario,rgm,created_at) VALUES (10,1,'12345678901','2026-11-02 12:00:00-03'::timestamptz);
                INSERT INTO pg_temp."USUARIOS" (id,nome,email,telefone,datanasc,senha,perfil,status,created_at)
                VALUES (2,'Professor','professor@example.com','11999990002','1990-01-01','hash','PROFESSORES',true,'2026-10-01'),
                       (3,'Outro professor','outro@example.com','11999990003','1990-01-01','hash','PROFESSORES',false,'2026-10-01');
                INSERT INTO pg_temp."PROFESSORES" (id,id_usuario,matricula,created_at)
                VALUES (20,2,'12345678902','2026-11-02 12:00:00-03'::timestamptz), (30,3,'12345678903','2026-11-02 12:00:00-03'::timestamptz);
                INSERT INTO pg_temp."USUARIOS" (id,nome,email,telefone,datanasc,senha,perfil,status,created_at)
                VALUES (4,'Admin','admin@example.com','11999990004','1990-01-01','hash','ADMINISTRADOR',true,'2026-11-02');
                INSERT INTO pg_temp."ADMINISTRADOR" (id,id_usuario,matricula,created_at,master)
                VALUES (40,4,'12345678904','2026-11-02 12:00:00-03',false);
                INSERT INTO pg_temp."TURMAS" (id,nome_turma,id_alunos,id_professores,id_proprietario,created_at)
                VALUES (100,'Turma A',ARRAY[10]::bigint[],ARRAY[20]::bigint[],20,'2026-11-02 12:00:00-03'::timestamptz),
                       (200,'Turma B',ARRAY[10]::bigint[],ARRAY[20]::bigint[],30,'2026-11-02 12:00:00-03'::timestamptz),
                       (300,'Turma privada',ARRAY[11]::bigint[],ARRAY[30]::bigint[],30,'2026-11-02 12:00:00-03'::timestamptz);
                INSERT INTO pg_temp."CONTEUDOS" (id,id_turma,id_professor,created_at,titulo,descricao,tipo,duracao_estimada,prioridade,status,revisao_solicitada)
                VALUES (1,ARRAY[100,200]::bigint[],20,'2026-11-02 12:00:00-03'::timestamptz,'Compartilhado','Texto','texto','30','baixa','ativo',false),
                       (2,ARRAY[100]::bigint[],20,'2026-11-02 12:00:00-03'::timestamptz,'Pendente','Texto','texto','30','baixa','ativo',false),
                       (3,ARRAY[300]::bigint[],30,'2026-11-02 12:00:00-03'::timestamptz,'Privado','Texto','texto','30','baixa','ativo',false),
                       (4,ARRAY[100]::bigint[],20,'2026-11-02 12:00:00-03'::timestamptz,'Suspenso','Texto','texto','30','baixa','suspenso_denuncia',true);
                INSERT INTO pg_temp."PROGRESSO_CONTEUDO" (id,id_aluno,id_conteudo,concluido_em)
                VALUES (1,10,1,'2026-11-01 12:00:00-03'),(2,11,2,'2026-11-01 12:00:00-03');
                INSERT INTO pg_temp."EVENTOS" (id,id_turma,id_professor,titulo,tipo,descricao,data_hora,data_fim,status,created_at,prioridade)
                VALUES (1,100,20,'Próximo','prova','Teste','2026-11-03 12:00','2026-11-03 16:00-03','ATIVO','2026-11-02 12:00:00-03'::timestamptz,'ALTA'),
                       (2,100,20,'Passado','prova','Teste','2026-10-31 12:00','2026-10-31 16:00-03','ATIVO','2026-11-02 12:00:00-03'::timestamptz,'BAIXA'),
                       (3,300,30,'Privado','prova','Teste','2026-11-03 12:00',null,'ATIVO','2026-11-02 12:00:00-03'::timestamptz,'ALTA');
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
        sql("UPDATE pg_temp.\"EVENTOS\" SET id_turma=200 WHERE id=1");
        var selecionada = repository.parametros("aluno",10,filtro("todos",100,"todos"));
        assertEquals(0L, repository.resumoAluno(selecionada).get("atrasados"));
        sql("UPDATE pg_temp.\"EVENTOS\" SET id_turma=300 WHERE id=1");
        assertEquals(1L, repository.resumoAluno(selecionada).get("atrasados"));
    }

    @Test
    void professorDistingueVinculosAutoriaEModeracao() throws Exception {
        sql("""
                INSERT INTO pg_temp."CONTEUDOS" (id,id_turma,id_professor,created_at,titulo,descricao,tipo,duracao_estimada,prioridade,status,revisao_solicitada)
                VALUES (5,ARRAY[100]::bigint[],30,'2026-11-02 12:00:00-03'::timestamptz,'De outro professor','Texto','texto','30','baixa','ativo',false);
                INSERT INTO pg_temp."DENUNCIAS" (id,id_conteudo,id_aluno,id_professor,motivo,status,created_at)
                VALUES (1,4,10,20,'Teste','pendente','2026-11-02 12:00:00-03'::timestamptz);
                """);
        var p = repository.parametros("professor",20,filtro("todos",0,"todos"));
        var turmas = repository.resumoTurmasProfessor(p);
        assertEquals(1L, turmas.get("gerenciadas"));
        assertEquals(1L, turmas.get("participantes"));
        assertEquals(1L, turmas.get("alunos"));
        assertEquals(3L, repository.resumoConteudos(p).get("conteudos"));
        assertEquals(2L, repository.resumoConteudos(p).get("ativos"));
        assertEquals(1L, repository.resumoConteudos(p).get("suspensos"));
        assertEquals(1L, repository.resumoConteudos(p).get("analise"));
        assertEquals(3L, repository.situacaoConteudos(p).stream().mapToLong(r -> ((Number)r.get("quantidade")).longValue()).sum());
        assertEquals(3L, repository.conteudosPorTurma(p).getFirst().get("quantidade"));
        assertEquals(2, repository.turmasProfessor(p).size());
        assertEquals(2L, repository.eventosPorMes(p).getFirst().get("quantidade"));
        assertEquals(1L, repository.listarConteudos(p,true).getFirst().get("denuncias"));
        assertEquals(3, repository.listarConteudos(p,false).size());
        assertNull(repository.buscarIdentidade("outro@example.com","professor"));
        sql("UPDATE pg_temp.\"CONTEUDOS\" SET created_at='2026-10-01 12:00-03' WHERE id=1");
        assertEquals(2L, repository.resumoConteudos(repository.parametros("professor",20,filtro("mes",0,"todos"))).get("conteudos"));
    }

    @Test
    void renderizaProfessorComDadosEVazio() throws Exception {
        var professor = new loginProfessorDTO(); professor.setEmail("professor@example.com");
        var session = new MockHttpSession();
        session.setAttribute("usuarioLogado",professor); session.setAttribute("tipoUsuario","professor");
        session.setAttribute("email2FA",professor.getEmail());
        String html = mvc().perform(get("/dashboard/professor").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Insights das Turmas"));
        assertEquals(1, html.split("href=\"/dashboard\"",-1).length - 1);
        assertTrue(html.contains("Participante"));
        assertFalse(html.contains("Turma privada"));
        mvc().perform(get("/dashboard/professor?turma=300").session(session)).andExpect(status().isForbidden());
        sql("UPDATE pg_temp.\"TURMAS\" SET id_professores=ARRAY[]::bigint[], id_proprietario=30");
        sql("DELETE FROM pg_temp.\"CONTEUDOS\" WHERE id_professor=20");
        mvc().perform(get("/dashboard/professor").session(session)).andExpect(status().isOk());
    }

    @Test
    void administradorContaPerfisInatividadeEDenunciasSemDuplicarConteudos() throws Exception {
        sql("""
                INSERT INTO pg_temp."DENUNCIAS" (id,id_conteudo,id_aluno,id_professor,motivo,status,created_at)
                VALUES (1,4,10,20,'Teste','pendente','2026-11-01 12:00-03'),
                       (2,4,11,20,'Teste','pendente','2026-11-01 12:00-03'),
                       (3,1,10,20,'Teste','analisada_liberada','2026-10-01 12:00-03');
                """);
        var p = repository.parametros("admin",40,filtro("todos",0,"todos"));
        var usuarios = repository.resumoUsuarios(p);
        assertEquals(4L, usuarios.get("usuarios"));
        assertEquals(3L, usuarios.get("ativos"));
        assertEquals(1L, usuarios.get("inativos"));
        assertEquals(2L, usuarios.get("professores"));
        assertEquals(1L, usuarios.get("administradores"));
        assertEquals(4, repository.usuariosRecentes(p).size());
        assertEquals(2, repository.usuariosPorMes(p).size());
        assertEquals(3L, repository.resumoTurmasAdmin(p).get("turmas"));
        assertEquals(2L, repository.resumoTurmasAdmin(p).get("responsaveis"));
        assertEquals(3L, repository.resumoTurmasAdmin(p).get("eventos"));
        assertEquals(3, repository.turmasAdmin(p).size());
        assertEquals(4L, repository.resumoConteudos(p).get("conteudos"));
        var denuncias = repository.resumoDenuncias(p);
        assertEquals(3L, denuncias.get("denuncias"));
        assertEquals(2L, denuncias.get("denunciados"));
        assertEquals(2L, denuncias.get("pendentes"));
        assertEquals(1L, denuncias.get("resolvidas"));
        assertEquals(1L, denuncias.get("liberados"));
        assertEquals(2, repository.denunciasPorSituacao(p).size());
        assertEquals(1, repository.listarConteudos(p,true).size());
        assertEquals(40L, repository.buscarIdentidade("admin@example.com","admin"));

        var filtrado = repository.parametros("admin",40,new DashboardFiltro("mes",0,"todos","ALUNOS",agora));
        assertEquals(1L, repository.resumoUsuarios(filtrado).get("usuarios"));
        assertEquals(2L, repository.resumoDenuncias(filtrado).get("denuncias"));
        assertEquals(4L, repository.resumoConteudos(filtrado).get("conteudos"));
    }

    @Test
    void renderizaAdminComFiltrosESemDados() throws Exception {
        var admin = new loginAdminDTO(); admin.setEmail("admin@example.com");
        var session = new MockHttpSession();
        session.setAttribute("usuarioLogado",admin); session.setAttribute("tipoUsuario","admin");
        session.setAttribute("email2FA",admin.getEmail());
        String html = mvc().perform(get("/dashboard/admin").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Insights Administrativos"));
        assertEquals(1, html.split("href=\"/dashboard\"",-1).length - 1);
        assertTrue(html.contains("Turma privada"));
        assertTrue(html.contains("/admin/usuarios/4/editar"));
        String filtrado = mvc().perform(get("/dashboard/admin?perfil=ALUNOS").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(filtrado.contains("/admin/usuarios/4/editar"));
        sql("DELETE FROM pg_temp.\"CONTEUDOS\"; DELETE FROM pg_temp.\"EVENTOS\"; DELETE FROM pg_temp.\"TURMAS\"; DELETE FROM pg_temp.\"USUARIOS\" WHERE id<>4");
        mvc().perform(get("/dashboard/admin?perfil=ALUNOS").session(session)).andExpect(status().isOk());
    }

    @Test
    void renderizaPaginaComDadosEVazia() throws Exception {
        var mvc = mvc();
        var session = new MockHttpSession();
        var aluno = new loginAlunoDTO(); aluno.setEmail("aluno@example.com");
        session.setAttribute("usuarioLogado", aluno);
        session.setAttribute("tipoUsuario", "aluno"); session.setAttribute("email2FA", aluno.getEmail());
        String html = mvc.perform(get("/dashboard/aluno").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("Insights dos Estudos"));
        assertEquals(1, html.split("href=\"/dashboard\"",-1).length - 1);
        assertTrue(html.contains("Turma A"));
        assertFalse(html.contains("Turma privada"));
        sql("UPDATE pg_temp.\"TURMAS\" SET id_alunos=ARRAY[]::bigint[]");
        mvc.perform(get("/dashboard/aluno").session(session)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        var resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(viewResolver.getTemplateEngine());
        resolver.setCharacterEncoding("UTF-8");
        return MockMvcBuilders.standaloneSetup(new DashboardController(repository, new DashboardService(repository)))
                .setViewResolvers(resolver).build();
    }

    private DashboardFiltro filtro(String periodo, long turma, String prioridade) {
        return new DashboardFiltro(periodo,turma,prioridade,"todos",agora);
    }

    private void sql(String sql) throws Exception {
        try (var stmt = conn.createStatement()) { stmt.execute(sql); }
    }
}
