// armazenamento externo realiza o envio a recuperacao e a exclusao dos arquivos
package com.example.CampusLink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;


// servico de armazenamento esta classe usa caminhos unicos e a pasta configurada para guardar materiais
@Service
public class SupabaseStorageService {

    private final RestClient restClient;
    private final String bucket;

    // preparar armazenamento externo este construtor recebe a conexao e a pasta dos arquivos
    public SupabaseStorageService(
            RestClient supabaseRestClient,
            @Value("${supabase.storage.bucket}") String bucket) {

        this.restClient = supabaseRestClient;
        this.bucket = bucket;
    }

    // enviar arquivo ao armazenamento este metodo cria um nome unico e envia os dados sem sobrescrever
    public String upload(MultipartFile arquivo) {

        // enviar arquivo ao armazenamento tenta preparar o caminho e transmitir os dados do arquivo
        try {

            String storagePath = gerarStoragePath(arquivo);

            String mimeType = arquivo.getContentType();

            // definir tipo do envio usa um tipo de arquivo generico quando o original nao informa um
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            restClient.post().uri("/storage/v1/object/{bucket}/{storagePath}", bucket, storagePath)
                    .header("x-upsert", "false")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(arquivo.getBytes())
                    .retrieve()
                    .toBodilessEntity();

            return storagePath;

        } catch (IOException e) {
            throw new RuntimeException("Não foi possível processar o arquivo.", e);
        }
    }

    // recuperar arquivo do armazenamento este metodo baixa os dados pelo caminho e recusa uma resposta vazia
    public byte[] download(String storagePath) {

        byte[] arquivo = restClient.get()
                .uri(
                        "/storage/v1/object/authenticated/{bucket}/{storagePath}",
                        bucket,
                        storagePath
                )
                .retrieve()
                .body(byte[].class);

        // conferir download do arquivo avisa quando o armazenamento nao retorna os dados
        if (arquivo == null) {

            throw new RuntimeException(
                    "Não foi possível recuperar o arquivo."
            );
        }

        return arquivo;
    }

    // delete arquivo na pasta configurada este metodo encaminha a remocao para o destino padrao
    public void excluir(String storagePath) {
        excluir(bucket, storagePath);
    }

    // delete arquivo na pasta informada este metodo solicita a remocao do caminho no armazenamento externo
    public void excluir(String bucketId, String storagePath) {

        Map<String, List<String>> body =
                Map.of("prefixes", List.of(storagePath));

        restClient.method(HttpMethod.DELETE)
                .uri("/storage/v1/object/{bucket}", bucketId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // criar nome unico do arquivo este metodo combina um identificador aleatorio com a extensao original em minusculas
    private String gerarStoragePath(
            MultipartFile arquivo) {

        String nomeOriginal = arquivo.getOriginalFilename();

        String extensao = "";

        // preservar extensao do arquivo aproveita o trecho depois do ultimo ponto no nome original
        if (nomeOriginal != null && nomeOriginal.contains(".")) {

            extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        }

        return UUID.randomUUID() + extensao.toLowerCase();
    }
}
