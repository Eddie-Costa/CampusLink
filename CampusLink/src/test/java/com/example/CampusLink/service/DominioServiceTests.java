// verificacao dos dominios confere a limpeza do endereco os pedidos repetidos e a analise do administrador
package com.example.CampusLink.service;

import com.example.CampusLink.dto.DominioDTO;
import com.example.CampusLink.repository.DominioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// testes dos dominios esta classe simula o banco das solicitacoes de dominio
class DominioServiceTests {

    private DominioRepository repositorio;
    private DominioService dominios;

    // preparar testes dos dominios este metodo cria o servico com o banco simulado
    @BeforeEach
    void preparar() {
        repositorio = mock(DominioRepository.class);
        dominios = new DominioService();
        ReflectionTestUtils.setField(dominios, "dominioRepository", repositorio);
    }

    // testar limpeza do endereco este teste confere que protocolo www porta caminho e maiusculas sao removidos
    @ParameterizedTest
    @CsvSource({"https://www.Khan.org/curso?x=1,khan.org", "site.com.br:8080,site.com.br", "  EXEMPLO.com.  ,exemplo.com",
            "ftp://arquivos.edu/pasta#topo,arquivos.edu"})
    void normalizaDominio(String entrada, String esperado) {
        // preparar endereco digitado pelo professor

        // executar limpeza
        String dominio = DominioService.normalizar(entrada);

        // verificar dominio limpo
        assertEquals(esperado, dominio);
    }

    // testar endereco invalido este teste confere a recusa de endereco vazio com usuario e sem extensao
    @ParameterizedTest
    @ValueSource(strings = {" ", "usuario:senha@site.com", "localhost", "site", "-site.com", "192.168.0.1"})
    void recusaDominioInvalido(String entrada) {
        // preparar endereco invalido

        // executar limpeza
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class, () -> DominioService.normalizar(entrada));

        // verificar mensagem de orientacao
        assertTrue(erro.getMessage().startsWith("Informe") || erro.getMessage().startsWith("Endereço inválido"), erro.getMessage());
    }

    // testar pedido de dominio este teste confere o dominio limpo a justificativa vazia e o professor gravados
    @Test
    void registraPedidoComDominioLimpo() throws SQLException {
        // preparar pedido com justificativa em branco
        DominioDTO pedido = pedido("https://www.khanacademy.org/math", "   ");

        // executar pedido
        dominios.solicitar(pedido, 4L);

        // verificar dados gravados
        verify(repositorio).inserir(pedido);
        assertEquals("khanacademy.org", pedido.getDominio());
        assertNull(pedido.getJustificativa());
        assertEquals(4L, pedido.getIdProfessor());
    }

    // testar pedido repetido este teste confere a recusa de dominio ja pedido mesmo quando dois pedidos chegam juntos
    @Test
    void recusaPedidoDeDominioRepetido() throws SQLException {
        // preparar dominio ja autorizado e pedido simultaneo barrado pela regra unica do banco
        when(repositorio.existePendenteOuAprovado("youtube.com")).thenReturn(true);
        doThrow(new SQLException("duplicado", "23505")).when(repositorio).inserir(any());

        // executar pedidos
        IllegalArgumentException repetido = assertThrows(IllegalArgumentException.class, () -> dominios.solicitar(pedido("youtube.com", "Videos"), 4L));
        IllegalArgumentException simultaneo = assertThrows(IllegalArgumentException.class, () -> dominios.solicitar(pedido("vimeo.com", "Videos"), 4L));

        // verificar mensagens amigaveis
        assertTrue(repetido.getMessage().contains("já está liberado"));
        assertTrue(simultaneo.getMessage().contains("vimeo.com"));
    }

    // testar analise do administrador este teste confere aprovacao rejeicao sem motivo comentario longo e pedido ja analisado
    @Test
    void analisaPedidoComRegrasDoComentario() throws SQLException {
        // preparar pedido 9 pendente e pedido 8 ja analisado
        when(repositorio.analisar(9L, "aprovado", "ok")).thenReturn(true);

        // executar analises
        dominios.analisar(9L, true, "  ok  ");
        String semMotivo = assertThrows(IllegalArgumentException.class, () -> dominios.analisar(9L, false, " ")).getMessage();
        String longo = assertThrows(IllegalArgumentException.class, () -> dominios.analisar(9L, true, "a".repeat(501))).getMessage();
        String jaAnalisado = assertThrows(IllegalArgumentException.class, () -> dominios.analisar(8L, false, "Fora do escopo")).getMessage();

        // verificar mensagens e gravacao apenas da analise valida
        assertEquals("Informe o motivo da rejeição.", semMotivo);
        assertTrue(longo.contains("500 caracteres"));
        assertTrue(jaAnalisado.contains("já foi analisada"));
        verify(repositorio, never()).analisar(9L, "rejeitado", null);
    }

    // testar falhas do banco este teste confere as mensagens de falha em cada operacao dos dominios
    @Test
    void trocaFalhasDoBancoPorMensagens() throws SQLException {
        // preparar banco indisponivel
        SQLException falha = new SQLException("fora do ar");
        when(repositorio.listarPorProfessor(4L)).thenThrow(falha);
        when(repositorio.listarPendentes(0)).thenThrow(falha);
        when(repositorio.listarAprovados(0)).thenThrow(falha);
        when(repositorio.hostAutorizado("site.com")).thenThrow(falha);
        when(repositorio.existePendenteOuAprovado("site.com")).thenThrow(falha);
        when(repositorio.analisar(9L, "aprovado", null)).thenThrow(falha);
        when(repositorio.analisar(7L, "aprovado", null)).thenThrow(new SQLException("duplicado", "23505"));

        // executar operacoes e verificar mensagens
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.listarPorProfessor(4L)).getMessage().contains("listar as solicitações"));
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.listarPendentes(0)).getMessage().contains("pendentes"));
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.listarAprovados(0)).getMessage().contains("sites liberados"));
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.hostAutorizado("site.com")).getMessage().contains("verificar o site"));
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.solicitar(pedido("site.com", null), 4L)).getMessage().contains("registrar"));
        assertTrue(assertThrows(RuntimeException.class, () -> dominios.analisar(9L, true, null)).getMessage().contains("analisar"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> dominios.analisar(7L, true, null)).getMessage().contains("outra solicitação"));
    }

    // montar pedido do teste este metodo cria o pedido com dominio e justificativa
    private DominioDTO pedido(String dominio, String justificativa) {
        DominioDTO pedido = new DominioDTO();
        pedido.setDominio(dominio);
        pedido.setJustificativa(justificativa);
        return pedido;
    }
}
