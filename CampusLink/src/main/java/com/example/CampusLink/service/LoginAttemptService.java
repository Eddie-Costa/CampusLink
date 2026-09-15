package com.example.CampusLink.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LoginAttemptService {
    private final int MAX_TENTATIVAS = 5;
    private final long TEMPO_BLOQUEIO = 10 * 60 * 1000; // 10 minutos

    private static Map<String, Integer> tentativas = new HashMap<>();
    private static Map<String, Long> bloqueados = new HashMap<>();

    public static boolean estaBloqueado(String email) {
        if (bloqueados.containsKey(email)) {
            long tempoRestante = bloqueados.get(email) - System.currentTimeMillis();

            if (tempoRestante > 0) {
                return true;
            } else {
                bloqueados.remove(email);
                tentativas.remove(email);
            }
        }
        return false;
    }

    public void loginFalhou(String email) {
        tentativas.put(email, tentativas.getOrDefault(email, 0) + 1);

        if (tentativas.get(email) >= MAX_TENTATIVAS) {
            bloqueados.put(email, System.currentTimeMillis() + TEMPO_BLOQUEIO);
        }
    }

    public void loginSucesso(String email) {
        tentativas.remove(email);
        bloqueados.remove(email);
    }

    public int getTentativas(String email) {
        return tentativas.getOrDefault(email, 0);
    }

}
