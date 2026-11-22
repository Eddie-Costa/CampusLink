// verificacao do banco de dominios confere pedidos dos professores analise do administrador e liberacao de links
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.DominioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes do banco de dominios esta classe usa o banco simulado com jdbctemplate real
class DominioRepositoryTests {

    private BancoSimulado banco;
    private DominioRepository dominios;

    // preparar testes dos dominios este metodo liga o repositorio ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        dominios = new DominioRepository(banco.jdbcTemplate());
    }

    // testar pedido de dominio este teste confere os dados enviados e a situacao devolvida pelo banco
    @Test
    void registraPedidoDeDominio() throws SQLException {
        // preparar pedido do professor
        DominioDTO dominio = new DominioDTO();
        dominio.setDominio("khanacademy.org");
        dominio.setIdProfessor(4L);
        dominio.setJustificativa("Exercicios");
        banco.quandoConsultar("INSERT INTO public.\"DOMINIOS\"", linha("id", 9L, "status", "pendente", "created_at", LocalDateTime.now()));

        // executar pedido
        dominios.inserir(dominio);

        // verificar parametros e retorno
        assertEquals(List.of("khanacademy.org", 4L, "Exercicios"), banco.ultimo("INSERT INTO").valores());
        assertEquals("pendente", dominio.getStatus());
        assertNotNull(dominio.getCreatedAt());
    }

    // testar listagens de dominios este teste confere pedidos do professor pendentes e aprovados
    @Test
    void listaDominiosPorSituacao() throws SQLException {
        // preparar dominio sem professor e sem analise
        banco.quandoConsultar("DOMINIOS", linha("id", 1L, "dominio", "youtube.com", "status", "aprovado",
                "id_professor", null, "analisado_em", null, "nome_professor", "Ana"));
        banco.quandoConsultar("SELECT COUNT(*) FROM public.\"DOMINIOS\"", linha("count", 25L));

        // executar listagens
        List<DominioDTO> doProfessor = dominios.listarPorProfessor(4L);
        var paginaPendentes = dominios.listarPendentes(9);
        List<DominioDTO> pendentes = paginaPendentes.getContent();
        List<DominioDTO> aprovados = dominios.listarAprovados(0).getContent();

        // verificar dados montados
        assertNull(doProfessor.get(0).getIdProfessor());
        assertNull(doProfessor.get(0).getAnalisadoEm());
        assertEquals("Ana", pendentes.get(0).getNomeProfessor());
        assertEquals("youtube.com", aprovados.get(0).getDominio());
        // 25 pedidos dao 3 paginas de 10; a pagina pedida alem do fim volta para a ultima
        assertEquals(2, paginaPendentes.getNumber());
        assertEquals(List.of(20), banco.ultimo("WHERE d.\"status\" = 'pendente'").valores());
    }

    // testar analise repetida este teste confere que so pedidos pendentes podem ser analisados
    @Test
    void analisaApenasPedidoPendente() throws SQLException {
        // preparar pedido ja analisado
        banco.quandoAlterar("AND \"status\" = 'pendente'", 0);

        // executar nova analise
        boolean analisou = dominios.analisar(9L, "aprovado", "ok");

        // verificar recusa e parametros
        assertFalse(analisou);
        assertEquals(List.of("aprovado", "ok", 9L), banco.ultimo("UPDATE public.\"DOMINIOS\"").valores());
    }

    // testar dominio liberado este teste confere a busca do host e de seus subdominios entre os aprovados
    @Test
    void confereHostLiberadoESubdominio() throws SQLException {
        // preparar host aprovado

        // executar conferencias
        boolean autorizado = dominios.hostAutorizado("www.youtube.com");
        boolean repetido = dominios.existePendenteOuAprovado("youtube.com");
        banco.linhasPadrao(0);
        boolean naoAutorizado = dominios.hostAutorizado("site.com");

        // verificar respostas e host enviado para as duas comparacoes
        assertTrue(autorizado);
        assertTrue(repetido);
        assertFalse(naoAutorizado);
        assertEquals(List.of("www.youtube.com", "www.youtube.com"), banco.comandos().get(0).valores());
    }

    // testar falha do banco este teste confere que o erro original chega ao servico
    @Test
    void repassaErroDoBanco() {
        // preparar falha
        banco.falharAo("DOMINIOS");

        // executar consulta
        SQLException erro = assertThrows(SQLException.class, () -> dominios.listarAprovados(0));

        // verificar mensagem original
        assertTrue(erro.getMessage().contains("falha simulada"));
    }
}
