package com.example.CampusLink.dao;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisaoConteudoDAOPersistenceTests {

    private JdbcTemplate jdbc;
    private RevisaoConteudoDAO dao;

    @BeforeEach
    void prepararBancoDeTeste() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:revisao_conteudo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public."CONTEUDOS" (
                    "id" BIGINT PRIMARY KEY,
                    "status" VARCHAR(40),
                    "comentario_admin" VARCHAR(255),
                    "revisao_solicitada" BOOLEAN,
                    "revisao_solicitada_em" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public."DENUNCIAS" (
                    "id" BIGINT PRIMARY KEY,
                    "id_conteudo" BIGINT,
                    "status" VARCHAR(40)
                )
                """);
        jdbc.update("DELETE FROM public.\"DENUNCIAS\"");
        jdbc.update("DELETE FROM public.\"CONTEUDOS\"");
        jdbc.update("""
                INSERT INTO public."CONTEUDOS"
                    ("id", "status", "comentario_admin", "revisao_solicitada", "revisao_solicitada_em")
                VALUES (56, 'suspenso_denuncia', 'análise pendente', TRUE, TIMESTAMP '2026-01-01 10:00:00')
                """);
        jdbc.update("INSERT INTO public.\"DENUNCIAS\" (\"id\", \"id_conteudo\", \"status\") VALUES (1, 56, 'pendente')");
        jdbc.update("INSERT INTO public.\"DENUNCIAS\" (\"id\", \"id_conteudo\", \"status\") VALUES (2, 56, 'analisada')");

        dao = new RevisaoConteudoDAO();
        ReflectionTestUtils.setField(dao, "dataSource", dataSource);
    }

    @Test
    void liberaConteudoEAtualizaSomenteDenunciasPendentesNoBanco() throws Exception {
        assertTrue(dao.liberarConteudo(56L));

        assertEquals("ativo", jdbc.queryForObject(
                "SELECT \"status\" FROM public.\"CONTEUDOS\" WHERE \"id\" = 56", String.class));
        assertNull(jdbc.queryForObject(
                "SELECT \"comentario_admin\" FROM public.\"CONTEUDOS\" WHERE \"id\" = 56", String.class));
        assertFalse(jdbc.queryForObject(
                "SELECT \"revisao_solicitada\" FROM public.\"CONTEUDOS\" WHERE \"id\" = 56", Boolean.class));
        assertNull(jdbc.queryForObject(
                "SELECT \"revisao_solicitada_em\" FROM public.\"CONTEUDOS\" WHERE \"id\" = 56", java.sql.Timestamp.class));
        assertEquals("analisada_liberada", jdbc.queryForObject(
                "SELECT \"status\" FROM public.\"DENUNCIAS\" WHERE \"id\" = 1", String.class));
        assertEquals("analisada", jdbc.queryForObject(
                "SELECT \"status\" FROM public.\"DENUNCIAS\" WHERE \"id\" = 2", String.class));
    }
}