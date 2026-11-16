// idade minima do cadastro concentra a regra de que so pessoas com 17 anos ou mais podem criar conta
package com.example.CampusLink.validation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

// regra de idade esta classe confere a data de nascimento usada nos cadastros de aluno professor e pelo administrador
public final class IdadeMinima {

    public static final int ANOS = 17;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private IdadeMinima() {
    }

    // validar nascimento este metodo devolve a mensagem de erro ou null quando a data existe e a pessoa ja tem 17 anos
    public static String validar(String dataNascimento) {
        return validar(dataNascimento, LocalDate.now(FUSO));
    }

    // validar nascimento em uma data este metodo permite testar a regra com um dia fixo
    static String validar(String dataNascimento, LocalDate hoje) {
        LocalDate nascimento;
        // interpretar nascimento recusa datas inexistentes ou fora do formato aaaa-mm-dd
        try {
            nascimento = LocalDate.parse(dataNascimento);
        } catch (DateTimeParseException | NullPointerException e) {
            return "Informe uma data de nascimento válida.";
        }

        // recusar data futura evita nascimento depois de hoje
        if (nascimento.isAfter(hoje)) {
            return "Informe uma data de nascimento válida.";
        }

        // exigir idade minima aceita quem completa 17 anos hoje ou antes
        if (nascimento.plusYears(ANOS).isAfter(hoje)) {
            return "É preciso ter pelo menos " + ANOS + " anos para se cadastrar no CampusLink.";
        }

        return null;
    }

    // data maxima de nascimento este metodo informa a tela qual o ultimo dia permitido no campo de data
    public static LocalDate nascimentoMaximo() {
        return LocalDate.now(FUSO).minusYears(ANOS);
    }
}
