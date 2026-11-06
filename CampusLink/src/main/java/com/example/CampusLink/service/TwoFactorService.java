// verificacao temporaria controla os codigos enviados por email
package com.example.CampusLink.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

// codigos de acesso esta classe mantem validade espera de reenvio e descarte depois do uso
@Service
public class TwoFactorService {

    private static final long TEMPO_EXPIRACAO = 5 * 60 * 1000L;
    private static final long INTERVALO_REENVIO = 2 * 60 * 1000L;
    private static final SecureRandom random = new SecureRandom();
    private static final Map<String, String> codigos = new HashMap<>();
    private static final Map<String, Long> expiracao = new HashMap<>();
    private static final Map<String, Long> proximoReenvio = new HashMap<>();

    // gerar codigo de verificacao este metodo cria seis digitos diferentes do anterior e define validade e espera
    public static synchronized String gerarCodigo(String chave) {

        // gerar codigo de verificacao exige uma chave preenchida
        if (chave == null || chave.isBlank()) {

            throw new IllegalArgumentException("a chave de verificacao e obrigatoria");
        }

        long agora = System.currentTimeMillis();
        removerCodigosExpirados(agora);
        String codigoAnterior = codigos.get(chave);
        String codigo;
        do {
            codigo = String.valueOf(random.nextInt(900000) + 100000);
        } while (codigo.equals(codigoAnterior));

        codigos.put(chave, codigo);
        expiracao.put(chave, agora + TEMPO_EXPIRACAO);
        proximoReenvio.put(chave, agora + INTERVALO_REENVIO);
        return codigo;
    }

    // reenviar codigo validado este metodo exige uma chave e respeita o intervalo antes de gerar outro codigo
    public String reenviarCodigo(String chave) {

        synchronized (TwoFactorService.class) {

            // reenviar codigo de verificacao exige uma chave preenchida
            if (chave == null || chave.isBlank()) {

                throw new IllegalArgumentException("a chave de verificacao e obrigatoria");
            }

            long segundosRestantes = segundosParaReenvio(chave);

            // limitar reenvio do codigo exige aguardar o intervalo entre envios
            if (segundosRestantes > 0) {

                throw new IllegalStateException("aguarde " + segundosRestantes + " segundos para solicitar outro codigo");
            }

            return gerarCodigo(chave);
        }
    }

    // consultar espera do codigo este metodo calcula os segundos restantes ate permitir novo envio
    public long segundosParaReenvio(String chave) {

        synchronized (TwoFactorService.class) {

            // consultar espera do codigo retorna zero quando nao existe uma chave
            if (chave == null || chave.isBlank()) {
                return 0;
            }

            Long horarioLiberacao = proximoReenvio.get(chave);

            // consultar liberacao do codigo retorna zero quando nao existe horario de reenvio
            if (horarioLiberacao == null) {
                return 0;
            }

            long milissegundosRestantes = horarioLiberacao - System.currentTimeMillis();

            // liberar reenvio do codigo retorna zero quando o intervalo ja terminou
            if (milissegundosRestantes <= 0) {
                return 0;
            }

            return (milissegundosRestantes + 999) / 1000;
        }
    }

    // consultar validade do codigo este metodo calcula o tempo restante e limpa codigos expirados
    public long segundosParaExpirarCodigo(String chave) {

        synchronized (TwoFactorService.class) {

            // consultar validade do codigo retorna zero quando nao existe uma chave
            if (chave == null || chave.isBlank()) {
                return 0;
            }

            Long horarioExpiracao = expiracao.get(chave);

            // consultar vencimento do codigo retorna zero quando nao existe horario de expiracao
            if (horarioExpiracao == null) {
                return 0;
            }

            long milissegundosRestantes = horarioExpiracao - System.currentTimeMillis();

            // delete codigo vencido limpa o codigo e seus horarios quando a validade termina
            if (milissegundosRestantes <= 0) {

                codigos.remove(chave);
                expiracao.remove(chave);
                proximoReenvio.remove(chave);
                return 0;
            }

            return (milissegundosRestantes + 999) / 1000;
        }
    }

    // consultar instante de expiracao este metodo retorna o horario limite salvo para a chave
    public long obterExpiracaoCodigo(String chave) {

        synchronized (TwoFactorService.class) {
            return expiracao.getOrDefault(chave, 0L);
        }
    }

    // validar codigo temporario este metodo compara o valor dentro do prazo e descarta o codigo depois do uso
    public boolean validarCodigo(
            String chave,
            String codigo
    ) {

        synchronized (TwoFactorService.class) {

            // validar codigo de verificacao rejeita chave ou codigo ausente
            if (chave == null || codigo == null) {
                return false;
            }

            String codigoSalvo = codigos.get(chave);
            Long horarioExpiracao = expiracao.get(chave);

            // conferir codigo salvo rejeita a verificacao sem codigo ou prazo registrado
            if (codigoSalvo == null || horarioExpiracao == null) {

                return false;
            }

            // rejeitar codigo expirado limpa o codigo quando o prazo de validade terminou
            if (System.currentTimeMillis() >= horarioExpiracao) {

                limparCodigo(chave);
                return false;
            }

            // conferir codigo informado rejeita um codigo diferente do valor salvo
            if (!codigoSalvo.equals(codigo.trim())) {
                return false;
            }


            limparCodigo(chave);
            return true;
        }
    }

    // delete codigo completo este metodo apaga valor validade e intervalo de reenvio da chave
    public void limparCodigo(String chave) {

        synchronized (TwoFactorService.class) {
            codigos.remove(chave);
            expiracao.remove(chave);
            proximoReenvio.remove(chave);
        }
    }

    // invalidar codigo mantendo espera este metodo descarta o valor e a validade sem liberar novo envio
    public void invalidarCodigo(String chave) {
        synchronized (TwoFactorService.class) {

            codigos.remove(chave);
            expiracao.remove(chave);
        }
    }

    // delete codigos expirados este metodo limpa os valores e intervalos cujo prazo terminou
    private static void removerCodigosExpirados(long agora) {

        expiracao.entrySet().removeIf(registro -> {

            // delete codigos expirados apaga os registros cujo prazo terminou
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
