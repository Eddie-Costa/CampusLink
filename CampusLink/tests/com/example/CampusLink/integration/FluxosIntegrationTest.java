package com.example.CampusLink.integration;

import com.example.CampusLink.dto.Aluno.loginAlunoDTO;
import com.example.CampusLink.dto.Professor.loginProfessorDTO;
import com.example.CampusLink.model.Disponibilidade;
import com.example.CampusLink.repository.DisponibilidadeRepository;
import com.example.CampusLink.dao.AdminTurmaDAO;
import com.example.CampusLink.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = BancoTesteInitializer.class)
class FluxosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisponibilidadeRepository disponibilidades;
    @Autowired AdminTurmaDAO turmas;
    @Value("${local.server.port}") int porta;

    @BeforeEach void preparar() {
        // Cada cenário cria seus próprios dados; o banco inteiro é descartável.
        jdbc.execute("TRUNCATE \"EVENTO_CONTEUDO\", \"EVENTOS\", \"DISPONIBILIDADES\", \"ARQUIVOS\", \"DENUNCIAS\", \"CONTEUDOS\", \"ALUNO_TURMA\", \"PROFESSOR_TURMA\", \"TURMAS\", \"ALUNOS\", \"PROFESSORES\", \"ADMINISTRADOR\", \"USUARIOS\", \"LOGS_EVENTOS\", \"LOGS_SESSOES\" RESTART IDENTITY CASCADE");
        jdbc.execute("""
                INSERT INTO "USUARIOS" (id,nome,email,perfil) VALUES (1,'Aluno','aluno-it@example.com','ALUNOS'),(2,'Professor','professor-it@example.com','PROFESSORES');
                INSERT INTO "ALUNOS" (id,rgm,id_usuario) VALUES (10,'12345678901',1);
                INSERT INTO "PROFESSORES" (id,matricula,id_usuario) VALUES (20,'12345678902',2);
                INSERT INTO "TURMAS" (id,nome_turma,id_proprietario,id_professores,id_alunos) VALUES (30,'Turma IT',20,ARRAY[20]::bigint[],ARRAY[10]::bigint[]);
                INSERT INTO "ALUNO_TURMA" (id_aluno,id_turma) VALUES (10,30);
                INSERT INTO "PROFESSOR_TURMA" (id_professor,id_turma) VALUES (20,30);
                INSERT INTO "CONTEUDOS" (id,id_turma,id_professor,titulo,tipo,status,prioridade) VALUES (40,ARRAY[30]::bigint[],20,'Material de teste','texto','ativo','MEDIA');
                """);
    }
    private MockHttpSession aluno() {
        var session = new MockHttpSession(); var aluno = new loginAlunoDTO(); aluno.setEmail("aluno-it@example.com");
        session.setAttribute("usuarioLogado", aluno); session.setAttribute("tipoUsuario", "aluno"); session.setAttribute("email2FA", aluno.getEmail()); return session;
    }
    private MockHttpSession professor() {
        var session = new MockHttpSession(); var professor = new loginProfessorDTO(); professor.setEmail("professor-it@example.com");
        session.setAttribute("usuarioLogado", professor); session.setAttribute("tipoUsuario", "professor"); session.setAttribute("email2FA", professor.getEmail()); return session;
    }
    @Test void deveConsultarConteudosComStatusECorpo() throws Exception {
        mvc.perform(get("/eventos/conteudos").param("turmaId", "30").session(professor()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[0].titulo").value("Material de teste"));
    }
    @Test void deveRetornar400ComCorpoQuandoParametroObrigatorioEstaAusente() throws Exception {
        // HTTP real inclui o despacho /error do servidor, sem um handler artificial de teste.
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/eventos/conteudos"))
                .header("Accept", "application/json").GET().build();
        try (var client = HttpClient.newHttpClient()) {
            var resposta = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(400, resposta.statusCode());
            var json = new tools.jackson.databind.ObjectMapper().readTree(resposta.body());
            assertEquals(400, json.get("status").asInt()); assertEquals("Bad Request", json.get("error").asString());
            assertEquals("/eventos/conteudos", json.get("path").asString());
        }
    }
    @Test void devePersistirERecuperarDisponibilidade() {
        var data = LocalDate.of(2026,10,23);
        var salva = disponibilidades.saveAndFlush(new Disponibilidade(10L, data, 4));
        var recuperada = disponibilidades.findByIdAlunoAndData(10L, data).orElseThrow();
        assertEquals(salva.getId(), recuperada.getId()); assertEquals(4, recuperada.getHorasDisponiveis());
        assertEquals(10L, recuperada.getIdAluno()); assertEquals(data, recuperada.getData());
    }
    @Test void deveCadastrarDisponibilidadeEConsultarCalendario() throws Exception {
        var session = aluno();
        mvc.perform(post("/disponibilidade/cadastrar").session(session).with(csrf()).param("data","2026-10-23").param("horasDisponiveis","4"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/disponibilidade"));
        assertEquals(4, disponibilidades.findByIdAlunoAndData(10L, LocalDate.of(2026,10,23)).orElseThrow().getHorasDisponiveis());
        mvc.perform(get("/disponibilidade").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-data=\"2026-10-23\"")))
                .andExpect(content().string(containsString("data-horas=\"4\"")));
    }
    @Test void deveAbrirFormularioDeDisponibilidade() throws Exception {
        mvc.perform(get("/disponibilidade/cadastrar").session(aluno())).andExpect(status().isOk())
                .andExpect(content().string(containsString("horasDisponiveis")));
    }
    @Test void deveCadastrarEExcluirEventoComConteudos() throws Exception {
        var session = professor();
        mvc.perform(post("/eventos/cadastrar").session(session).with(csrf()).param("nome","Prova IT").param("inicio","2026-10-24T14:00")
                        .param("fim","2026-10-24T16:00").param("turmaId","30").param("conteudoIds","40"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/eventos"));
        Long id = jdbc.queryForObject("SELECT id FROM \"EVENTOS\" WHERE titulo='Prova IT'", Long.class);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"EVENTO_CONTEUDO\" WHERE id_evento=? AND id_conteudo=40", Integer.class, id));
        mvc.perform(post("/eventos/excluir").session(session).with(csrf()).param("eventoId",id.toString())).andExpect(redirectedUrl("/eventos"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"EVENTOS\" WHERE id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"EVENTO_CONTEUDO\" WHERE id_evento=?", Integer.class, id));
    }
    @Test void deveLiberarConteudoEAtualizarDenunciasNoBanco() throws Exception {
        jdbc.execute("UPDATE \"CONTEUDOS\" SET status='suspenso_denuncia', revisao_solicitada=true WHERE id=40");
        jdbc.execute("INSERT INTO \"DENUNCIAS\" (id_conteudo,id_aluno,id_professor,motivo) VALUES (40,10,20,'Motivo')");
        mvc.perform(post("/admin/conteudos-denunciados/40/liberar").session(AdminFixture.sessao()).with(csrf()))
                .andExpect(redirectedUrl("/admin/conteudos-denunciados")).andExpect(flash().attributeExists("mensagemSucesso"));
        assertEquals("ativo", jdbc.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id=40", String.class));
        assertEquals("analisada_liberada", jdbc.queryForObject("SELECT status FROM \"DENUNCIAS\" WHERE id_conteudo=40", String.class));
    }
    @Test void deveRegistrarDenunciaPeloEndpoint() throws Exception {
        mvc.perform(post("/turmas/30/conteudos/40/denunciar").session(aluno()).with(csrf()).param("motivo", "  Informação incorreta  "))
                .andExpect(redirectedUrl("/turmas/30")).andExpect(flash().attribute("mensagemSucesso", "Denúncia registrada com sucesso."));
        assertEquals("Informação incorreta", jdbc.queryForObject("SELECT motivo FROM \"DENUNCIAS\" WHERE id_conteudo=40 AND id_aluno=10", String.class));
        assertEquals("ativo", jdbc.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id=40", String.class));
    }
    @Test void deveReprovarConteudoEEncerrarDenunciasNoBanco() throws Exception {
        jdbc.execute("UPDATE \"CONTEUDOS\" SET status='suspenso_denuncia', revisao_solicitada=true WHERE id=40");
        jdbc.execute("INSERT INTO \"DENUNCIAS\" (id_conteudo,id_aluno,id_professor,motivo) VALUES (40,10,20,'Motivo')");
        mvc.perform(post("/admin/conteudos-denunciados/40/reprovar").session(AdminFixture.sessao()).with(csrf()).param("comentarioAdmin", "  Conteúdo incorreto  "))
                .andExpect(redirectedUrl("/admin/conteudos-denunciados")).andExpect(flash().attributeExists("mensagemSucesso"));
        assertEquals("removido", jdbc.queryForObject("SELECT status FROM \"CONTEUDOS\" WHERE id=40", String.class));
        assertEquals("Conteúdo incorreto", jdbc.queryForObject("SELECT comentario_admin FROM \"CONTEUDOS\" WHERE id=40", String.class));
        assertEquals("analisada_removida", jdbc.queryForObject("SELECT status FROM \"DENUNCIAS\" WHERE id_conteudo=40", String.class));
    }
    @Test void deveAdicionarERemoverAlunoMantendoVinculosConsistentes() throws Exception {
        jdbc.execute("INSERT INTO \"USUARIOS\" (id,nome,email,perfil) VALUES (3,'Outro','outro-it@example.com','ALUNOS')");
        jdbc.execute("INSERT INTO \"ALUNOS\" (id,rgm,id_usuario) VALUES (11,'12345678903',3)");
        assertTrue(turmas.adicionarAlunoNaTurma(30L,11L));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"ALUNO_TURMA\" WHERE id_turma=30 AND id_aluno=11", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT 11 = ANY(id_alunos) FROM \"TURMAS\" WHERE id=30", Boolean.class));
        assertTrue(turmas.removerAlunoDaTurma(30L,11L));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"ALUNO_TURMA\" WHERE id_turma=30 AND id_aluno=11", Integer.class));
        assertFalse(jdbc.queryForObject("SELECT 11 = ANY(id_alunos) FROM \"TURMAS\" WHERE id=30", Boolean.class));
    }
}
