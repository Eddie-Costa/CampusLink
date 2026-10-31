package com.example.CampusLink.controller.Geral;

import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ArquivoControllerIntegrationTest extends IntegracaoHttpBase {

    @Test
    void listar_comArquivoCadastrado_deveRetornar200ComNomeETipo() throws Exception {
        criarArquivo();

        var resposta = professor.get().uri("/arquivos").retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(nomeArquivo, "application/pdf");
    }

    @Test
    void upload_comArquivoValido_deveRedirecionarEPersistirMetadados() throws Exception {
        var resposta = enviarArquivo();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/arquivos");
        conferirPagina(professor, "/arquivos", "Arquivo enviado com sucesso.");
        var arquivo = arquivos.buscarMaisRecentePorConteudo(conteudoId);
        assertThat(arquivo).isNotNull();
        assertThat(arquivo.getId()).isPositive();
        assertThat(arquivo.getIdConteudo()).isEqualTo(conteudoId);
        assertThat(arquivo.getNomeOriginal()).isEqualTo(nomeArquivo);
        assertThat(arquivo.getMimeType()).isEqualTo("application/pdf");
        assertThat(arquivo.getTamanhoBytes()).isEqualTo(DADOS.length);
        assertThat(arquivo.getStoragePath()).isEqualTo(storagePath);
    }

    @Test
    void excluir_comArquivoExistente_deveRedirecionarERemoverDoBanco() throws Exception {
        long id = criarArquivo();

        var resposta = postar(professor, "/arquivos/" + id + "/excluir", formulario());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(resposta.getHeaders().getLocation()).hasPath("/arquivos");
        conferirPagina(professor, "/arquivos", "Arquivo excluído com sucesso.");
        assertThat(arquivos.buscarPorId(id)).isNull();
        verify(storage).excluir(storagePath);
    }

    @Test
    void download_comPermissao_deveRetornar200ComArquivoECabecalhos() throws Exception {
        long id = criarArquivo();

        var resposta = aluno.get().uri("/arquivos/" + id + "/download").retrieve().toEntity(byte[].class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo(DADOS);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resposta.getHeaders().getContentLength()).isEqualTo(DADOS.length);
        var disposicao = ContentDisposition.parse(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposicao.getType()).isEqualTo("attachment");
        assertThat(disposicao.getFilename()).isEqualTo(nomeArquivo);
    }

    @Test
    void download_semLogin_deveRetornar401SemEntregarArquivo() throws Exception {
        long id = criarArquivo();

        var resposta = novoCliente().get().uri("/arquivos/" + id + "/download").retrieve().toEntity(byte[].class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).isNullOrEmpty();
        verify(storage, never()).download(anyString());
    }

    @Test
    void download_comAlunoSemPermissao_deveRetornar403SemEntregarArquivo() throws Exception {
        long id = criarArquivo();
        jdbc.update("UPDATE \"CONTEUDOS\" SET status = 'suspenso_denuncia' WHERE id = ?", conteudoId);

        var resposta = aluno.get().uri("/arquivos/" + id + "/download").retrieve().toEntity(byte[].class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).isNullOrEmpty();
        verify(storage, never()).download(anyString());
    }

    @Test
    void upload_semArquivo_deveRetornar400ComCorpoDeErro() throws Exception {
        var dados = new LinkedMultiValueMap<String, Object>();
        dados.add("idConteudo", Long.toString(conteudoId));

        var resposta = professor.post().uri("/arquivos/upload").header("X-CSRF-TOKEN", csrf(professor))
                .contentType(MediaType.MULTIPART_FORM_DATA).accept(MediaType.APPLICATION_JSON)
                .body(dados).retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("\"status\":400", "Bad Request", "/arquivos/upload");
        assertThat(arquivos.buscarMaisRecentePorConteudo(conteudoId)).isNull();
        verify(storage, never()).upload(any());
    }

    @Test
    void download_comIdInvalido_deveRetornar400ComCorpoDeErro() {
        var resposta = aluno.get().uri("/arquivos/invalido/download").accept(MediaType.APPLICATION_JSON)
                .retrieve().toEntity(String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).contains("\"status\":400", "Bad Request", "/arquivos/invalido/download");
        verify(storage, never()).download(anyString());
    }

    @Test
    void download_comMetodoPost_deveRetornar405ComCorpoDeErro() throws Exception {
        long id = criarArquivo();

        var resposta = postar(aluno, "/arquivos/" + id + "/download", formulario());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resposta.getBody()).contains("\"status\":405", "Method Not Allowed", "/arquivos/" + id + "/download");
        assertThat(arquivos.buscarPorId(id)).isNotNull();
    }
}
