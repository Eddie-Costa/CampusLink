// catalogo de livros consulta o google books para apoiar a selecao de materiais
package com.example.CampusLink.service;

import com.example.CampusLink.dto.GoogleBookDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// busca bibliografica esta classe organiza resultados detalhes paginacao e repeticoes de consultas temporariamente indisponiveis
@Service
public class GoogleBooksService {

    private static final int MAX_TENTATIVAS = 3;
    private final RestClient restClient;
    private final String apiKey;

    // preparar busca externa de livros este construtor recebe a conexao e a chave de acesso
    public GoogleBooksService(
            RestClient googleBooksRestClient,
            @Value("${googlebooks.api.key}") String apiKey) {

        this.restClient = googleBooksRestClient;
        this.apiKey = apiKey;
    }

    // buscar primeira pagina de livros este metodo consulta os resultados iniciais do termo informado
    public List<GoogleBookDTO> buscarLivros(String termo) {
        return buscarLivros(termo, 0).livros();
    }

    // buscar pagina externa de livros este metodo organiza os volumes recebidos e calcula a proxima pagina
    public PaginaLivros buscarLivros(String termo, int startIndex) {
        VolumesResponse resposta = buscarVolumes(termo, startIndex);

        // buscar livros retorna uma pagina vazia quando a resposta nao possui itens
        if (resposta == null || resposta.items == null) {
            return new PaginaLivros(List.of(), null);
        }

        List<GoogleBookDTO> resultado = new ArrayList<>();

        for (VolumeItem item : resposta.items) {

            // listar livros ignora resultados sem dados do livro
            if (item.volumeInfo == null) {
                continue;
            }

            resultado.add(converterLivro(item.volumeInfo));
        }

        int proximoIndice = startIndex + resposta.items.size();
        Integer proximoStartIndex = !resposta.items.isEmpty()
                && resposta.totalItems != null
                && proximoIndice < resposta.totalItems
                ? proximoIndice
                : null;
        return new PaginaLivros(resultado, proximoStartIndex);
    }

    // consultar volume externo este metodo busca os detalhes de um livro pelo identificador
    public GoogleBookDTO buscarDetalhesLivro(String volumeId) {
        VolumeDetails resposta = repetirSeIndisponivel(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes/{volumeId}")
                        .queryParam("key", apiKey)
                        .build(volumeId))
                .retrieve()
                .body(VolumeDetails.class));

        return resposta == null || resposta.volumeInfo == null
                ? null
                : converterLivro(resposta.volumeInfo);
    }

    // consultar volumes externos este metodo pede ate dez livros a partir da posicao informada
    private VolumesResponse buscarVolumes(String termo, int startIndex) {
        return repetirSeIndisponivel(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/volumes")
                        .queryParam("q", termo)
                        .queryParam("maxResults", 10)
                        .queryParam("startIndex", startIndex)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(VolumesResponse.class));
    }

    // repetir consulta de livros este metodo tenta novamente quando o provedor apresenta falha temporaria
    private <T> T repetirSeIndisponivel(Supplier<T> requisicao) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            // repetir busca de livros tenta executar a consulta e trata indisponibilidade do servico
            try {
                return requisicao.get();
            } catch (RestClientResponseException e) {
                // encerrar tentativas de livros repassa erros fora do servidor ou apos o limite de tentativas
                if (!e.getStatusCode().is5xxServerError() || tentativa == MAX_TENTATIVAS) {
                    throw e;
                }

                // aguardar nova busca de livros faz uma pausa crescente e respeita a interrupcao
                try {
                    Thread.sleep(250L * tentativa);
                } catch (InterruptedException interrupcao) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Busca no Google Books interrompida.", interrupcao);
                }
            }
        }

        throw new IllegalStateException("Não foi possível concluir a busca no Google Books.");
    }

    // montar livro encontrado este metodo organiza titulo autores editora ano codigo capa e endereco
    private GoogleBookDTO converterLivro(VolumeInfo volumeInfo) {
        GoogleBookDTO livro = new GoogleBookDTO();
        livro.setTitulo(volumeInfo.title);
        livro.setAutores(formatarAutores(volumeInfo.authors));
        livro.setEditora(volumeInfo.publisher);
        livro.setAnoPublicacao(extrairAno(volumeInfo.publishedDate));
        livro.setIsbn(extrairIsbn(volumeInfo.industryIdentifiers));

        // mostrar capa do livro usa a imagem quando o resultado fornece um endereco
        if (volumeInfo.imageLinks != null) {
            livro.setThumbnailUrl(volumeInfo.imageLinks.thumbnail);
        }

        livro.setLinkGoogleBooks(volumeInfo.infoLink);
        return livro;
    }

    // formatar autores do livro este metodo junta os nomes ou indica autor desconhecido
    private String formatarAutores(List<String> autores) {

        // mostrar autores do livro informa autor desconhecido quando nao ha autores
        if (autores == null || autores.isEmpty()) {
            return "Autor desconhecido";
        }
        return String.join(", ", autores);
    }

    // extrair ano do livro este metodo usa os quatro primeiros caracteres da data disponivel
    private String extrairAno(String dataPublicacao) {

        // extrair ano do livro retorna vazio quando a data nao permite obter quatro caracteres
        if (dataPublicacao == null || dataPublicacao.length() < 4) {
            return "";
        }
        return dataPublicacao.substring(0, 4);
    }

    // escolher codigo do livro este metodo prefere o identificador de treze digitos ao de dez
    private String extrairIsbn(List<IndustryIdentifier> identificadores) {

        // buscar isbn do livro retorna vazio quando nao existem identificadores
        if (identificadores == null) {
            return "";
        }

        for (IndustryIdentifier id : identificadores) {
            // escolher isbn longo retorna o identificador de treze digitos
            if ("ISBN_13".equals(id.type)) {
                return id.identifier;
            }
        }

        for (IndustryIdentifier id : identificadores) {
            // escolher isbn curto retorna o identificador de dez digitos
            if ("ISBN_10".equals(id.type)) {
                return id.identifier;
            }
        }

        return "";
    }



    // resposta da busca de livros esta classe recebe a lista de volumes e o total encontrado
    private static class VolumesResponse {
        public List<VolumeItem> items;
        public Integer totalItems;
    }

    // item da busca de livros esta classe recebe os dados de um volume na lista retornada
    private static class VolumeItem {
        public VolumeInfo volumeInfo;
    }

    // resposta dos detalhes do livro esta classe recebe as informacoes do volume consultado individualmente
    private static class VolumeDetails {
        public VolumeInfo volumeInfo;
    }

    // informacoes externas do livro esta classe recebe titulo autores editora data codigos capa e link
    private static class VolumeInfo {
        public String title;
        public List<String> authors;
        public String publisher;
        public String publishedDate;
        public List<IndustryIdentifier> industryIdentifiers;
        public ImageLinks imageLinks;
        public String infoLink;
    }

    // identificador bibliografico esta classe recebe o tipo e o valor do codigo do livro
    private static class IndustryIdentifier {
        public String type;
        public String identifier;
    }

    // capa externa do livro esta classe recebe o endereco da miniatura
    private static class ImageLinks {
        public String thumbnail;
    }

    // pagina de livros este registro junta os resultados organizados e a posicao da proxima consulta
    public record PaginaLivros(List<GoogleBookDTO> livros, Integer nextStartIndex) {
    }
}