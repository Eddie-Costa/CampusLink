// tentativas de acesso controlam as falhas de senha por email
package com.example.CampusLink.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// bloqueio de login esta classe conta erros e aplica uma espera depois do limite de tentativas
@Service
public class LoginAttemptService {
    private final int MAX_TENTATIVAS = 5;
    private final long TEMPO_BLOQUEIO = 10 * 60 * 1000;

    private static Map<String, Integer> tentativas = new HashMap<>();
    private static Map<String, Long> bloqueados = new HashMap<>();

    // verificar bloqueio de login este metodo confere o prazo e limpa bloqueios que ja terminaram
    public static boolean estaBloqueado(String email) {
        // verificar bloqueio de login calcula o tempo restante para emails bloqueados
        if (bloqueados.containsKey(email)) {
            long tempoRestante = bloqueados.get(email) - System.currentTimeMillis();

            // liberar login vencido mantem o bloqueio ativo ou limpa as tentativas quando ele termina
            if (tempoRestante > 0) {
                return true;
            } else {
                bloqueados.remove(email);
                tentativas.remove(email);
            }
        }
        return false;
    }

    // registrar falha de login este metodo conta a tentativa e aplica o bloqueio ao atingir o limite
    public void loginFalhou(String email) {
        tentativas.put(email, tentativas.getOrDefault(email, 0) + 1);

        // bloquear tentativas de login define o prazo de bloqueio quando atinge o limite de erros
        if (tentativas.get(email) >= MAX_TENTATIVAS) {
            bloqueados.put(email, System.currentTimeMillis() + TEMPO_BLOQUEIO);
        }
    }

    // limpar tentativas de login este metodo remove falhas e bloqueios apos uma entrada bem sucedida
    public void loginSucesso(String email) {
        tentativas.remove(email);
        bloqueados.remove(email);
    }

    // consultar tentativas de login este metodo retorna quantas falhas foram registradas para o email
    public int getTentativas(String email) {
        return tentativas.getOrDefault(email, 0);
    }

}
