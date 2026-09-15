package com.example.CampusLink.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

@Service
public class TwoFactorService {

    private static final long TEMPO_EXPIRACAO = 5 * 60 * 1000L;
    private static final long INTERVALO_REENVIO = 2 * 60 * 1000L;
    private static final SecureRandom random = new SecureRandom();
    private static final Map<String, String> codigos = new HashMap<>();
    private static final Map<String, Long> expiracao = new HashMap<>();
    private static final Map<String, Long> proximoReenvio = new HashMap<>();

    public static synchronized String gerarCodigo(String chave) {

        if (chave == null || chave.isBlank()) {

            throw new IllegalArgumentException("a chave de verificacao e obrigatoria");
        }

        long agora = System.currentTimeMillis();
        removerCodigosExpirados(agora);
        String codigo = String.valueOf(random.nextInt(900000) + 100000);

        codigos.put(chave, codigo);
        expiracao.put(chave, agora + TEMPO_EXPIRACAO);
        proximoReenvio.put(chave, agora + INTERVALO_REENVIO);
        return codigo;
    }

    public String reenviarCodigo(String chave) {

        synchronized (TwoFactorService.class) {

            if (chave == null || chave.isBlank()) {

                throw new IllegalArgumentException("a chave de verificacao e obrigatoria");
            }

            if (!codigos.containsKey(chave) && !expiracao.containsKey(chave)) {

                throw new IllegalStateException("nao existe uma verificacao ativa para esta chave");
            }

            long segundosRestantes = segundosParaReenvio(chave);

            if (segundosRestantes > 0) {

                throw new IllegalStateException("aguarde " + segundosRestantes + " segundos para solicitar outro codigo");
            }

            return gerarCodigo(chave);
        }
    }

    public long segundosParaReenvio(String chave) {

        synchronized (TwoFactorService.class) {

            if (chave == null || chave.isBlank()) {
                return 0;
            }

            Long horarioLiberacao = proximoReenvio.get(chave);

            if (horarioLiberacao == null) {
                return 0;
            }

            long milissegundosRestantes = horarioLiberacao - System.currentTimeMillis();

            if (milissegundosRestantes <= 0) {
                return 0;
            }

            return (milissegundosRestantes + 999) / 1000;
        }
    }

    public long segundosParaExpirarCodigo(String chave) {

        synchronized (TwoFactorService.class) {

            if (chave == null || chave.isBlank()) {
                return 0;
            }

            Long horarioExpiracao = expiracao.get(chave);

            if (horarioExpiracao == null) {
                return 0;
            }

            long milissegundosRestantes = horarioExpiracao - System.currentTimeMillis();

            if (milissegundosRestantes <= 0) {

                codigos.remove(chave);
                expiracao.remove(chave);
                proximoReenvio.remove(chave);
                return 0;
            }

            return (milissegundosRestantes + 999) / 1000;
        }
    }

    public long obterExpiracaoCodigo(String chave) {

        synchronized (TwoFactorService.class) {
            return expiracao.getOrDefault(chave, 0L);
        }
    }

    public boolean validarCodigo(
            String chave,
            String codigo
    ) {

        synchronized (TwoFactorService.class) {

            if (chave == null || codigo == null) {
                return false;
            }

            String codigoSalvo = codigos.get(chave);
            Long horarioExpiracao = expiracao.get(chave);

            if (codigoSalvo == null || horarioExpiracao == null) {

                return false;
            }

            if (System.currentTimeMillis() >= horarioExpiracao) {

                limparCodigo(chave);
                return false;
            }

            if (!codigoSalvo.equals(codigo.trim())) {
                return false;
            }

            // limpa o codigo depois do uso
            limparCodigo(chave);
            return true;
        }
    }

    public void limparCodigo(String chave) {

        synchronized (TwoFactorService.class) {
            codigos.remove(chave);
            expiracao.remove(chave);
            proximoReenvio.remove(chave);
        }
    }

    private static void removerCodigosExpirados(long agora) {

        expiracao.entrySet().removeIf(registro -> {

            if (agora >= registro.getValue()) {

                String chave = registro.getKey();
                codigos.remove(chave);
                proximoReenvio.remove(chave);
                return true;
            }

            return false;
        });
    }
}