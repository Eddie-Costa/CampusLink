package com.example.CampusLink.dao;

import com.example.CampusLink.service.SupabaseStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.test.context.ContextConfiguration(initializers = com.example.CampusLink.support.BancoTesteInitializer.class)
@SpringBootTest
class PerfilDadosTests {
    @Autowired DataSource dataSource;
    private Connection conn;
    private usuarioDAO usuarios;
    private SupabaseStorageService storage;

    @BeforeEach
    void prepararTabelasTemporarias() throws Exception {
        conn = dataSource.getConnection();
        conn.setAutoCommit(true);
        // Somente a estrutura é lida do banco. Todos os dados e alterações do teste
        // ficam em pg_temp, isolados das tabelas reais e descartados ao final.
        for (String tabela : new String[]{"USUARIOS", "ALUNOS", "PROFESSORES", "ADMINISTRADOR", "TURMAS",
                "ALUNO_TURMA", "PROFESSOR_TURMA", "DISPONIBILIDADES", "DENUNCIAS", "CONTEUDOS", "EVENTOS",
                "EVENTO_CONTEUDO", "ARQUIVOS"}) {
            sql("CREATE TEMP TABLE \"" + tabela + "\" (LIKE public.\"" + tabela + "\" INCLUDING CONSTRAINTS)");
        }
        sql("ALTER TABLE pg_temp.\"USUARIOS\" ADD PRIMARY KEY (id)");
        sql("ALTER TABLE pg_temp.\"ALUNOS\" ADD PRIMARY KEY (id)");
        sql("ALTER TABLE pg_temp.\"PROFESSORES\" ADD PRIMARY KEY (id)");
        sql("ALTER TABLE pg_temp.\"DENUNCIAS\" ADD FOREIGN KEY (id_aluno) REFERENCES pg_temp.\"ALUNOS\"(id)");
        sql("ALTER TABLE pg_temp.\"DENUNCIAS\" ADD FOREIGN KEY (id_professor) REFERENCES pg_temp.\"PROFESSORES\"(id)");

        Connection isolada = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("prepareStatement")) {
                        args[0] = ((String) args[0]).replace("public.", "pg_temp.");
                    }
                    try {
                        return method.invoke(conn, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        DataSource temporario = mock(DataSource.class);
        when(temporario.getConnection()).thenReturn(isolada);
        usuarios = new usuarioDAO();
        storage = mock(SupabaseStorageService.class);
        ReflectionTestUtils.setField(usuarios, "dataSource", temporario);
        ReflectionTestUtils.setField(usuarios, "storage", storage);

        sql("""
                INSERT INTO pg_temp."USUARIOS" VALUES
                (1, 'Aluno teste', 'aluno@example.com', '11999990001', '2000-01-01', 'hash', 'ALUNOS', true, CURRENT_DATE),
                (2, 'Professor teste', 'professor@example.com', '11999990002', '1990-01-01', 'hash', 'PROFESSORES', true, CURRENT_DATE),
                (3, 'Outro professor', 'outro@example.com', '11999990003', '1990-01-01', 'hash', 'PROFESSORES', true, CURRENT_DATE);
                INSERT INTO pg_temp."ALUNOS" (id, created_at, rgm, id_usuario) VALUES (10, now(), '12345678901', 1);
                INSERT INTO pg_temp."PROFESSORES" (id, matricula, created_at, id_usuario) VALUES
                    (20, '12345678902', now(), 2), (30, '12345678903', now(), 3);
                INSERT INTO pg_temp."TURMAS" (id, nome_turma, id_professores, id_alunos, created_at, id_proprietario)
                    VALUES (100, 'Turma de teste', ARRAY[20,30]::bigint[], ARRAY[10]::bigint[], now(), 30);
                INSERT INTO pg_temp."ALUNO_TURMA" VALUES (1, 10, 100, 'ativo', now());
                INSERT INTO pg_temp."PROFESSOR_TURMA" VALUES (1, 20, 100, now());
                INSERT INTO pg_temp."DISPONIBILIDADES" VALUES (1, 10, CURRENT_DATE, 2, now());
                INSERT INTO pg_temp."DENUNCIAS" VALUES (1, 500, 10, 20, 'Teste', 'pendente', now());
                """);
    }

    @AfterEach
    void descartarTabelasTemporarias() throws Exception {
        if (conn != null) {
            try {
                if (!conn.getAutoCommit()) conn.rollback();
                conn.setAutoCommit(true);
                sql("DISCARD TEMP");
            } finally {
                conn.close();
            }
        }
    }

    @Test
    void consultaDadosCadastraisDeAlunoEProfessor() throws Exception {
        var aluno = usuarios.consultarDadosPessoais("ALUNO@example.com");
        assertEquals("12345678901", aluno.getIdentificador());
        assertEquals("Aluno", aluno.getTipoConta());
        assertEquals("Matrícula", usuarios.consultarDadosPessoais("professor@example.com").getRotuloIdentificador());
        assertNull(usuarios.consultarDadosPessoais("inexistente@example.com"));
    }

    @Test
    void excluiAlunoEVinculosPreservandoOutrosUsuariosETurma() throws Exception {
        usuarios.excluirDadosPessoais(1, "aluno@example.com");
        assertEquals(0, quantidade("USUARIOS", "id = 1"));
        assertEquals(0, quantidade("ALUNOS", "id = 10"));
        assertEquals(0, quantidade("ALUNO_TURMA", "id_aluno = 10"));
        assertEquals(0, quantidade("DISPONIBILIDADES", "id_aluno = 10"));
        assertEquals(0, quantidade("DENUNCIAS", "id_aluno = 10"));
        assertEquals(1, quantidade("TURMAS", "id = 100 AND cardinality(id_alunos) = 0"));
        assertEquals(2, quantidade("USUARIOS", "id <> 1"));
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"turma", "material", "evento"})
    void impedeExcluirProfessorComVinculosAtivos(String vinculo) throws Exception {
        switch (vinculo) {
            case "turma" -> sql("UPDATE pg_temp.\"TURMAS\" SET id_proprietario = 20 WHERE id = 100");
            case "material" -> material("ativo");
            case "evento" -> sql("""
                    INSERT INTO pg_temp."EVENTOS" (id, id_turma, id_professor, titulo, tipo, descricao, data_hora, status, created_at)
                    VALUES (1, 100, 20, 'Teste', 'aula', 'Teste', now(), 'ativo', now())
                    """);
        }
        assertTrue(usuarios.possuiVinculosParaExclusao(2));
        assertThrows(IllegalStateException.class, () -> usuarios.excluirDadosPessoais(2, "professor@example.com"));
        assertEquals(1, quantidade("USUARIOS", "id = 2"));
        assertEquals(1, quantidade("PROFESSOR_TURMA", "id_professor = 20"));
        verifyNoInteractions(storage);
    }

    @Test
    void excluiProfessorComMaterialJaRemovidoELimpaArquivos() throws Exception {
        material("removido");
        sql("""
                INSERT INTO pg_temp."ARQUIVOS" (id, id_conteudo, created_at, bucket_id, storage_path)
                    VALUES (1, 500, now(), 'arquivos-teste', 'material-teste.pdf');
                INSERT INTO pg_temp."EVENTO_CONTEUDO" VALUES (1, 999, 500);
                """);
        assertFalse(usuarios.possuiVinculosParaExclusao(2));
        usuarios.excluirDadosPessoais(2, "professor@example.com");
        verify(storage).excluir("arquivos-teste", "material-teste.pdf");
        assertEquals(0, quantidade("CONTEUDOS", "id_professor = 20"));
        assertEquals(0, quantidade("ARQUIVOS", "id_conteudo = 500"));
        assertEquals(0, quantidade("EVENTO_CONTEUDO", "id_conteudo = 500"));
        assertEquals(0, quantidade("PROFESSORES", "id = 20"));
        assertEquals(1, quantidade("TURMAS", "id = 100 AND id_professores = ARRAY[30]::bigint[]"));
        assertEquals(1, quantidade("USUARIOS", "id = 1"));
    }

    @Test
    void erroNoFinalDesfazExclusaoDeTodosOsVinculos() throws Exception {
        sql("CREATE TEMP TABLE impedimento (usuario_id integer REFERENCES pg_temp.\"USUARIOS\"(id))");
        sql("INSERT INTO pg_temp.impedimento VALUES (1)");
        assertThrows(SQLException.class, () -> usuarios.excluirDadosPessoais(1, "aluno@example.com"));
        assertEquals(1, quantidade("USUARIOS", "id = 1"));
        assertEquals(1, quantidade("ALUNO_TURMA", "id_aluno = 10"));
        assertEquals(1, quantidade("DISPONIBILIDADES", "id_aluno = 10"));
        assertEquals(1, quantidade("DENUNCIAS", "id_aluno = 10"));
        assertEquals(1, quantidade("TURMAS", "10 = ANY(id_alunos)"));
    }

    @Test
    void naoExcluiIdentidadeDiferente() throws Exception {
        assertThrows(IllegalStateException.class, () -> usuarios.excluirDadosPessoais(1, "professor@example.com"));
        assertEquals(3, quantidade("USUARIOS", "true"));
    }

    private void material(String status) throws SQLException {
        sql("""
                INSERT INTO pg_temp."CONTEUDOS" (id, id_turma, id_professor, created_at, titulo, descricao,
                    tipo, duracao_estimada, prioridade, status, revisao_solicitada)
                VALUES (500, ARRAY[100]::bigint[], 20, now(), 'Teste', 'Teste', 'texto', '30', 'baixa', '%s', false)
                """.formatted(status));
    }

    private long quantidade(String tabela, String condicao) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT count(*) FROM pg_temp.\"" + tabela + "\" WHERE " + condicao)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void sql(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
