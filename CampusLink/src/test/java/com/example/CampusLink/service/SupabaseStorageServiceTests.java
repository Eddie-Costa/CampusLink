// verificacao do armazenamento do supabase confere os enderecos e corpos enviados no envio download e exclusao
package com.example.CampusLink.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// testes do armazenamento esta classe simula o servidor do supabase sem acessar a internet
class SupabaseStorageServiceTests {

    private static final String SERVIDOR = "http://storage.teste";

    private MockRestServiceServer servidor;
    private SupabaseStorageService storage;

    // preparar testes do armazenamento este metodo liga o cliente http a um servidor simulado
    @BeforeEach
    void preparar() {
        RestClient.Builder cliente = RestClient.builder().baseUrl(SERVIDOR);
        servidor = MockRestServiceServer.bindTo(cliente).build();
        storage = new SupabaseStorageService(cliente.build(), "materiais");
    }

    // conferir chamadas este metodo garante que todas as chamadas esperadas aconteceram
    @AfterEach
    void conferir() {
        servidor.verify();
    }

    // testar envio do arquivo este teste confere nome aleatorio com a extensao original e envio sem sobrescrever
    @Test
    void enviaArquivoComNomeAleatorio() {
        // preparar servidor que aceita o envio
        servidor.expect(request -> assertTrue(request.getURI().toString().matches(SERVIDOR + "/storage/v1/object/materiais/[0-9a-f-]{36}\\.pdf")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-upsert", "false"))
                .andRespond(withSuccess());

        // executar envio
        String caminho = storage.upload(new MockMultipartFile("arquivo", "Aula.PDF", "application/pdf", new byte[]{1}));

        // verificar caminho devolvido
        assertTrue(caminho.endsWith(".pdf"));
    }

    // testar envio sem tipo este teste confere o tipo generico quando o navegador nao informa o tipo
    @Test
    void enviaArquivoSemTipoComoBinario() {
        // preparar servidor que espera tipo generico
        servidor.expect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/octet-stream"))
                .andRespond(withSuccess());

        // executar envio de arquivo sem extensao e sem tipo
        String caminho = storage.upload(new MockMultipartFile("arquivo", "semextensao", null, new byte[]{1}));

        // verificar caminho sem extensao
        assertTrue(caminho.matches("[0-9a-f-]{36}"));
    }

    // testar download este teste confere o endereco autenticado e o arquivo vazio
    @Test
    void baixaArquivoAutenticado() {
        // preparar servidor com o arquivo e depois sem corpo
        servidor.expect(requestToUriTemplate(SERVIDOR + "/storage/v1/object/authenticated/{bucket}/{caminho}", "materiais", "abc.pdf"))
                .andRespond(withSuccess(new byte[]{7}, MediaType.APPLICATION_OCTET_STREAM));
        servidor.expect(requestTo(SERVIDOR + "/storage/v1/object/authenticated/materiais/vazio.pdf"))
                .andRespond(withSuccess());

        // executar downloads
        byte[] dados = storage.download("abc.pdf");
        RuntimeException erro = assertThrows(RuntimeException.class, () -> storage.download("vazio.pdf"));

        // verificar conteudo e mensagem
        assertArrayEquals(new byte[]{7}, dados);
        assertTrue(erro.getMessage().contains("recuperar o arquivo"));
    }

    // testar exclusao este teste confere o pedido de remocao com o caminho do arquivo no corpo
    @Test
    void excluiArquivoPeloCaminho() {
        // preparar servidor que aceita a exclusao
        servidor.expect(requestTo(SERVIDOR + "/storage/v1/object/materiais"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("{\"prefixes\":[\"abc.pdf\"]}"))
                .andRespond(withSuccess());

        // executar exclusao
        storage.excluir("abc.pdf");

        // verificar pedido conferido pelo servidor simulado no metodo conferir
    }
}
