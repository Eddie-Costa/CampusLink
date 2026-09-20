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


@Service
public class SupabaseStorageService {

    private final RestClient restClient;
    private final String bucket;

    public SupabaseStorageService(
            RestClient supabaseRestClient,
            @Value("${supabase.storage.bucket}") String bucket) {

        this.restClient = supabaseRestClient;
        this.bucket = bucket;
    }

    public String upload(MultipartFile arquivo) {

        try {

            String storagePath = gerarStoragePath(arquivo);

            String mimeType = arquivo.getContentType();

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

    public byte[] download(String storagePath) {

        byte[] arquivo = restClient.get()
                .uri(
                        "/storage/v1/object/authenticated/{bucket}/{storagePath}",
                        bucket,
                        storagePath
                )
                .retrieve()
                .body(byte[].class);

        if (arquivo == null) {

            throw new RuntimeException(
                    "Não foi possível recuperar o arquivo."
            );
        }

        return arquivo;
    }

    public void excluir(String storagePath) {

        Map<String, List<String>> body =
                Map.of("prefixes", List.of(storagePath));

        restClient.method(HttpMethod.DELETE)
                .uri("/storage/v1/object/{bucket}", bucket)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private String gerarStoragePath(
            MultipartFile arquivo) {

        String nomeOriginal = arquivo.getOriginalFilename();

        String extensao = "";

        if (nomeOriginal != null && nomeOriginal.contains(".")) {

            extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf("."));
        }

        return UUID.randomUUID() + extensao.toLowerCase();
    }
}