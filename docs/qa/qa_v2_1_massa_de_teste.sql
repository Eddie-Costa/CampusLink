-- Massa de teste v2 (branch feature/viabilidade-risco-v2)
-- Pode ser executado quantas vezes quiser: apaga as turmas [QA] e recria tudo com datas relativas a HOJE.
-- O resultado final mostra a tabela "dia relativo -> data real" usada no guia de testes.

DO $$
DECLARE
    d0 date := (now() AT TIME ZONE 'America/Sao_Paulo')::date;
    aluno bigint := 151;
    prof bigint := 26;
    prof_ficticio bigint := 32;
    ana bigint := 29;
    bruno bigint := 30;
    tq bigint;
    ta bigint;
    tb bigint;
    c1 bigint; c2 bigint; c3 bigint; c4 bigint; c5 bigint; c6 bigint;
    c7 bigint; c8 bigint; c9 bigint; c10 bigint; c11 bigint; c12 bigint;
    cb1 bigint; cb2 bigint;
    e0 bigint; e1 bigint; e2 bigint; e3 bigint; e4 bigint; e5 bigint; eb0 bigint;
BEGIN
    FOR tq IN SELECT id FROM "TURMAS" WHERE nome_turma LIKE '[QA]%' LOOP
        DELETE FROM "PROGRESSO_CONTEUDO" WHERE id_conteudo IN (SELECT id FROM "CONTEUDOS" WHERE tq = ANY(id_turma));
        DELETE FROM "DENUNCIAS" WHERE id_conteudo IN (SELECT id FROM "CONTEUDOS" WHERE tq = ANY(id_turma));
        DELETE FROM "EVENTO_CONTEUDO" WHERE id_evento IN (SELECT id FROM "EVENTOS" WHERE id_turma = tq);
        DELETE FROM "EVENTO_CONTEUDO" WHERE id_conteudo IN (SELECT id FROM "CONTEUDOS" WHERE tq = ANY(id_turma));
        DELETE FROM "EVENTOS" WHERE id_turma = tq;
        DELETE FROM "CONTEUDOS" WHERE tq = ANY(id_turma);
        DELETE FROM "ALUNO_TURMA" WHERE id_turma = tq;
        DELETE FROM "PROFESSOR_TURMA" WHERE id_turma = tq;
        DELETE FROM "TURMAS" WHERE id = tq;
    END LOOP;

    -- Isola os 3 alunos: tira da turma 52 (que tem eventos próprios e bagunçaria os números)
    UPDATE "TURMAS" SET id_alunos = array_remove(array_remove(array_remove(id_alunos, aluno), ana), bruno) WHERE id = 52;
    DELETE FROM "ALUNO_TURMA" WHERE id_turma = 52 AND id_aluno IN (aluno, ana, bruno);
    DELETE FROM "DISPONIBILIDADES" WHERE id_aluno IN (aluno, ana, bruno);
    DELETE FROM "PROGRESSO_CONTEUDO" WHERE id_aluno IN (aluno, ana, bruno);

    -- Turma A: o professor Eddie é o dono
    INSERT INTO "TURMAS" (nome_turma, descricao, id_professores, id_alunos, id_proprietario)
    VALUES ('[QA] Planejamento de Estudos', 'Turma de testes v2 (dono: professor Eddie).', ARRAY[prof, prof_ficticio], ARRAY[aluno, ana, bruno], prof)
    RETURNING id INTO ta;
    INSERT INTO "PROFESSOR_TURMA" (id_professor, id_turma) VALUES (prof, ta), (prof_ficticio, ta);
    INSERT INTO "ALUNO_TURMA" (id_aluno, id_turma, situacao_aluno) VALUES (aluno, ta, 'verde'), (ana, ta, 'verde'), (bruno, ta, 'verde');

    -- Turma B: a dona é a professora fictícia; o professor Eddie é só membro
    INSERT INTO "TURMAS" (nome_turma, descricao, id_professores, id_alunos, id_proprietario)
    VALUES ('[QA] Turma da Prof. Ana Almeida', 'Turma de testes v2 (dona: professora fictícia).', ARRAY[prof_ficticio, prof], ARRAY[aluno], prof_ficticio)
    RETURNING id INTO tb;
    INSERT INTO "PROFESSOR_TURMA" (id_professor, id_turma) VALUES (prof_ficticio, tb), (prof, tb);
    INSERT INTO "ALUNO_TURMA" (id_aluno, id_turma, situacao_aluno) VALUES (aluno, tb, 'verde');

    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 01 - Introdução', '1h. Já concluído pelo aluno Eddie.', 'texto', '60', 60, 'alta', 'ativo') RETURNING id INTO c1;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 02 - Variáveis (em 2 eventos)', '2h. Ligado à Prova 1 e à Prova final.', 'texto', '120', 120, 'alta', 'ativo') RETURNING id INTO c2;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 03 - Comentários (sem duração)', 'Sem duração cadastrada: o sistema considera 1h.', 'texto', '', NULL, 'media', 'ativo') RETURNING id INTO c3;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 04 - Laços', '4h.', 'texto', '240', 240, 'media', 'ativo') RETURNING id INTO c4;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 05 - Recursão', '5h.', 'texto', '300', 300, 'alta', 'ativo') RETURNING id INTO c5;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 06 - Projeto final', '50h: não cabe até a Prova final.', 'texto', '3000', 3000, 'media', 'ativo') RETURNING id INTO c6;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 07 - Lista de exercícios (vencida)', '1h. O evento já passou.', 'texto', '60', 60, 'media', 'ativo') RETURNING id INTO c7;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 08 - Leitura dirigida (vencida e feita)', '30min. O evento já passou; o aluno Eddie concluiu antes.', 'texto', '30', 30, 'media', 'ativo') RETURNING id INTO c8;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status, reaberto_em)
    VALUES (ARRAY[ta], prof, 'QA 09 - Lista reaberta', '45min. O evento passou, mas o professor re-disponibilizou.', 'texto', '45', 45, 'media', 'ativo', now()) RETURNING id INTO c9;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 10 - Material complementar (sem evento)', '45min. Não está ligado a nenhum evento.', 'texto', '45', 45, 'baixa', 'ativo') RETURNING id INTO c10;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof, 'QA 11 - Exercícios extras (sem evento)', '10h. Sem evento; o aluno Eddie concluiu há 2 dias.', 'texto', '600', 600, 'baixa', 'ativo') RETURNING id INTO c11;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[ta], prof_ficticio, 'QA 12 - Texto da Prof. Ana Almeida (vencido)', '45min. Autora: professora fictícia.', 'texto', '45', 45, 'media', 'ativo') RETURNING id INTO c12;

    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[tb], prof_ficticio, 'QA B1 - Conteúdo da Prof. Ana Almeida (vencido)', '1h. Autora e dona da turma: professora fictícia.', 'texto', '60', 60, 'media', 'ativo') RETURNING id INTO cb1;
    INSERT INTO "CONTEUDOS" (id_turma, id_professor, titulo, descricao, tipo, duracao_estimada, duracao_estimada_min, prioridade, status)
    VALUES (ARRAY[tb], prof, 'QA B2 - Conteúdo do professor Eddie (vencido)', '1h. Autor: professor Eddie.', 'texto', '60', 60, 'media', 'ativo') RETURNING id INTO cb2;

    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Quiz passado', 'PROVA', 'Evento que já passou.', (d0 - 3) + TIME '10:00', ((d0 - 3) + TIME '11:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA') RETURNING id INTO e0;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Lista reaberta', 'TRABALHO', 'Evento que já passou; o conteúdo foi re-disponibilizado.', (d0 - 1) + TIME '10:00', ((d0 - 1) + TIME '11:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA') RETURNING id INTO e5;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Prova 1', 'PROVA', 'Esperado para o aluno Eddie: Possível.', (d0 + 3) + TIME '19:00', ((d0 + 3) + TIME '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA') RETURNING id INTO e1;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Trabalho 2', 'TRABALHO', 'Mesmo dia do Seminário 3, prioridade média, horário mais cedo.', (d0 + 5) + TIME '10:00', ((d0 + 5) + TIME '12:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA') RETURNING id INTO e2;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Seminário 3', 'APRESENTACAO', 'Mesmo dia do Trabalho 2, prioridade alta, horário mais tarde.', (d0 + 5) + TIME '20:00', ((d0 + 5) + TIME '21:30') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA') RETURNING id INTO e3;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (ta, prof, 'QA Prova final', 'PROVA', 'Esperado para o aluno Eddie: Impossível.', (d0 + 9) + TIME '19:00', ((d0 + 9) + TIME '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA') RETURNING id INTO e4;
    INSERT INTO "EVENTOS" (id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade)
    VALUES (tb, prof_ficticio, 'QA B Entrega passada', 'TRABALHO', 'Evento da turma B que já passou.', (d0 - 2) + TIME '10:00', ((d0 - 2) + TIME '11:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA') RETURNING id INTO eb0;

    INSERT INTO "EVENTO_CONTEUDO" (id_evento, id_conteudo) VALUES
        (e0, c7), (e0, c8), (e0, c12),
        (e5, c9),
        (e1, c1), (e1, c2), (e1, c3),
        (e2, c4),
        (e3, c5),
        (e4, c2), (e4, c6),
        (eb0, cb1), (eb0, cb2);

    -- Disponibilidade do aluno Eddie. D+3 é dia de prova: as 4h dele NÃO podem ser contadas.
    INSERT INTO "DISPONIBILIDADES" (id_aluno, data, horas_disponiveis) VALUES
        (aluno, d0, 2), (aluno, d0 + 1, 2), (aluno, d0 + 2, 1), (aluno, d0 + 3, 4), (aluno, d0 + 4, 2),
        (aluno, d0 + 6, 1), (aluno, d0 + 7, 1), (aluno, d0 + 8, 1);

    INSERT INTO "PROGRESSO_CONTEUDO" (id_aluno, id_conteudo, concluido_em) VALUES
        (aluno, c1, now() - INTERVAL '1 day'),
        (aluno, c11, now() - INTERVAL '2 days'),
        (aluno, c8, now() - INTERVAL '10 days'),
        (ana, c1, now() - INTERVAL '1 day'),
        (ana, c2, now() - INTERVAL '1 day'),
        (ana, c7, now() - INTERVAL '4 days'),
        (ana, c8, now() - INTERVAL '4 days'),
        (ana, c12, now() - INTERVAL '4 days'),
        (bruno, c8, now() - INTERVAL '5 days');
END $$;

WITH base AS (SELECT (now() AT TIME ZONE 'America/Sao_Paulo')::date AS d0)
SELECT CASE WHEN n = 0 THEN 'D0 (hoje)' WHEN n > 0 THEN 'D+' || n ELSE 'D' || n END AS dia_relativo,
       to_char(d0 + n, 'DD/MM/YYYY') AS data_real,
       (SELECT string_agg(e.titulo || ' ' || to_char(e.data_hora, 'HH24:MI') || ' [id_turma=' || e.id_turma || ', id_evento=' || e.id || ']', ' | ' ORDER BY e.data_hora)
        FROM "EVENTOS" e JOIN "TURMAS" t ON t.id = e.id_turma
        WHERE t.nome_turma LIKE '[QA]%' AND e.data_hora::date = d0 + n) AS eventos_do_dia
FROM base, generate_series(-3, 10) AS n
ORDER BY n;
