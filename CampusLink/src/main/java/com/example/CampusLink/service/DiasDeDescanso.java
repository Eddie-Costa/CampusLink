package com.example.CampusLink.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

// dias de descanso esta classe diz quais datas sao fim de semana ou feriado nacional, quando o plano nao sugere horas extras
public final class DiasDeDescanso {

    // feriados nacionais de data fixa
    private static final Set<MonthDay> FERIADOS_FIXOS = Set.of(
            MonthDay.of(1, 1),    // confraternizacao universal
            MonthDay.of(4, 21),   // tiradentes
            MonthDay.of(5, 1),    // dia do trabalho
            MonthDay.of(9, 7),    // independencia
            MonthDay.of(10, 12),  // nossa senhora aparecida
            MonthDay.of(11, 2),   // finados
            MonthDay.of(11, 15),  // proclamacao da republica
            MonthDay.of(11, 20),  // dia nacional de zumbi e da consciencia negra
            MonthDay.of(12, 25)   // natal
    );

    private DiasDeDescanso() {
    }

    // verificar descanso este metodo aceita sabado domingo e feriados nacionais fixos e moveis
    public static boolean ehDescanso(LocalDate dia) {
        DayOfWeek semana = dia.getDayOfWeek();
        // fim de semana e sempre descanso
        if (semana == DayOfWeek.SATURDAY || semana == DayOfWeek.SUNDAY) {
            return true;
        }
        // feriado fixo compara so dia e mes
        if (FERIADOS_FIXOS.contains(MonthDay.from(dia))) {
            return true;
        }
        LocalDate pascoa = pascoa(dia.getYear());
        // feriados moveis contam a partir da pascoa: carnaval, sexta-feira santa e corpus christi
        return dia.equals(pascoa.minusDays(48)) || dia.equals(pascoa.minusDays(47))
                || dia.equals(pascoa.minusDays(2)) || dia.equals(pascoa.plusDays(60));
    }

    // calcular pascoa este metodo usa o algoritmo de meeus para o calendario gregoriano
    static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int diaDoMes = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, diaDoMes);
    }
}
