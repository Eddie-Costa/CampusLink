// verificacao dos bancos de arquivos e de materiais dos eventos confere gravacao leitura e troca das associacoes
package com.example.CampusLink.repository;

import com.example.CampusLink.dto.ArquivoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.example.CampusLink.repository.BancoSimulado.linha;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// testes de arquivos e materiais dos eventos esta classe usa o banco simulado com jdbctemplate real
class ArquivoEventoConteudoRepositoryTests {

    private BancoSimulado banco;
    private ArquivoRepository arquivos;
    private EventoConteudoRepository eventoConteudos;

    // preparar testes de arquivos e eventos este metodo liga os dois repositorios ao banco simulado
    @BeforeEach
    void preparar() {
        banco = new BancoSimulado();
        arquivos = new ArquivoRepository(banco.jdbcTemplate());
        eventoConteudos = new EventoConteudoRepository(banco.jdbcTemplate());
    }

    // testar registro do arquivo este teste confere os dados do arquivo enviado ao armazenamento
    @Test
    void registraArquivoDoMaterial() throws SQLException {
        // preparar arquivo enviado
        ArquivoDTO arquivo = new ArquivoDTO();
        arquivo.setIdConteudo(10L);
        arquivo.setBucketId("materiais");
        arquivo.setStoragePath("10/aula.pdf");
        arquivo.setNomeOriginal("aula.pdf");
        arquivo.setMimeType("application/pdf");
        arquivo.setTamanhoBytes(2048L);

        // executar registro
        arquivos.inserir(arquivo);

        // verificar parametros
        assertEquals(List.of(10L, "materiais", "10/aula.pdf", "aula.pdf", "application/pdf", 2048L),
                banco.ultimo("INSERT INTO public.\"ARQUIVOS\"").valores());
    }

    // testar leitura dos arquivos este teste confere arquivo com autor arquivo sem material e arquivo inexistente
    @Test
    void leArquivosComESemMaterial() throws SQLException {
        // preparar arquivo com autor e arquivo sem material
        UUID autor = UUID.randomUUID();
        banco.quandoConsultar("ORDER BY \"created_at\" DESC\n",
                linha("id", 1L, "id_conteudo", 10L, "uploaded_by", autor, "created_at", LocalDateTime.now()),
                linha("id", 2L, "id_conteudo", null, "uploaded_by", null, "created_at", null));
        banco.semResultado("WHERE \"id\" = ?");

        // executar consultas
        List<ArquivoDTO> todos = arquivos.listarTodos();
        ArquivoDTO maisRecente = arquivos.buscarMaisRecentePorConteudo(10L);
        ArquivoDTO inexistente = arquivos.buscarPorId(99L);
        arquivos.excluir(1L);

        // verificar dados e exclusao
        assertEquals(autor, todos.get(0).getUploadedBy());
        assertNull(todos.get(1).getIdConteudo());
        assertNull(todos.get(1).getCreatedAt());
        assertEquals(1L, maisRecente.getId());
        assertNull(inexistente);
        assertEquals(List.of(1L), banco.ultimo("DELETE FROM public.\"ARQUIVOS\"").valores());
    }

    // testar falha no arquivo este teste confere que o erro original do banco chega ao servico
    @Test
    void repassaErroAoLerArquivo() {
        // preparar falha
        banco.falharAo("ARQUIVOS");

        // executar consulta
        SQLException erro = assertThrows(SQLException.class, () -> arquivos.buscarPorId(1L));

        // verificar mensagem
        assertTrue(erro.getMessage().contains("falha simulada"));
    }

    // testar materiais do evento este teste confere que codigos vazios repetidos ou nulos sao ignorados
    @Test
    void associaMateriaisSemRepetirNemNulos() throws SQLException {
        // preparar lista com repeticao e nulo

        // executar associacoes validas e invalidas
        eventoConteudos.associarConteudo(5L, 10L);
        eventoConteudos.associarConteudo(null, 10L);
        eventoConteudos.associarConteudos(5L, Arrays.asList(10L, null, 10L, 11L));
        eventoConteudos.associarConteudos(5L, List.of());
        eventoConteudos.associarConteudos(null, List.of(10L));

        // verificar um vinculo simples e dois vinculos no lote
        assertEquals(List.of(5L, 10L), banco.comandos().get(0).valores());
        assertEquals(3, banco.comandos().size());
        assertEquals(List.of(5L, 11L), banco.comandos().get(2).valores());
    }

    // testar troca dos materiais este teste confere que os vinculos antigos saem antes dos novos
    @Test
    void substituiMateriaisDoEvento() throws SQLException {
        // preparar nova lista com repeticao

        // executar troca exclusao e pedido sem evento
        eventoConteudos.substituirConteudosDoEvento(5L, Arrays.asList(11L, 11L, null, 12L));
        eventoConteudos.substituirConteudosDoEvento(6L, null);
        eventoConteudos.excluirAssociacoesDoEvento(7L);
        eventoConteudos.excluirAssociacoesDoEvento(null);

        // verificar ordem e erro sem evento
        assertTrue(banco.comandos().get(0).sql().startsWith("DELETE"));
        assertEquals(List.of(5L, 11L), banco.comandos().get(1).valores());
        assertEquals(List.of(5L, 12L), banco.comandos().get(2).valores());
        assertEquals(List.of(6L), banco.comandos().get(3).valores());
        assertEquals(List.of(7L), banco.comandos().get(4).valores());
        assertEquals(5, banco.comandos().size());
        assertThrows(IllegalArgumentException.class, () -> eventoConteudos.substituirConteudosDoEvento(null, List.of(1L)));
    }
}
