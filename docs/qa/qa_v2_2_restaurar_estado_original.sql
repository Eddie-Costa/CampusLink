-- Desfaz a massa de teste v2: apaga as turmas [QA] e devolve os dados reais
-- dos alunos 151 (Eddie aluno), 29 (Ana) e 30 (Bruno) como estavam em 01/11/2026.

DO $$
DECLARE
    tq bigint;
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

    DELETE FROM "DISPONIBILIDADES" WHERE id_aluno IN (151, 29, 30);
    DELETE FROM "PROGRESSO_CONTEUDO" WHERE id_aluno IN (151, 29, 30);

    UPDATE "TURMAS" SET id_alunos = array_append(id_alunos, 151::bigint) WHERE id = 52 AND NOT (151 = ANY(id_alunos));
    UPDATE "TURMAS" SET id_alunos = array_append(id_alunos, 29::bigint) WHERE id = 52 AND NOT (29 = ANY(id_alunos));
    UPDATE "TURMAS" SET id_alunos = array_append(id_alunos, 30::bigint) WHERE id = 52 AND NOT (30 = ANY(id_alunos));

    INSERT INTO "ALUNO_TURMA" (id_aluno, id_turma, situacao_aluno)
    SELECT a, 52, 'verde' FROM unnest(ARRAY[151, 29, 30]) AS a
    WHERE NOT EXISTS (SELECT 1 FROM "ALUNO_TURMA" WHERE id_aluno = a AND id_turma = 52);

    INSERT INTO "DISPONIBILIDADES" (id_aluno, data, horas_disponiveis) VALUES
        (151, DATE '2026-10-25', 0), (151, DATE '2026-10-26', 0), (151, DATE '2026-10-29', 7),
        (151, DATE '2026-10-30', 24), (151, DATE '2026-11-01', 2), (151, DATE '2026-11-02', 10),
        (151, DATE '2026-11-05', 1);

    INSERT INTO "PROGRESSO_CONTEUDO" (id_aluno, id_conteudo, concluido_em) VALUES
        (151, 2677, TIMESTAMPTZ '2026-10-24 21:52:30.2378+00'),
        (151, 2678, TIMESTAMPTZ '2026-10-24 22:07:15.473347+00'),
        (151, 2679, TIMESTAMPTZ '2026-10-30 02:09:38.188983+00'),
        (151, 2674, TIMESTAMPTZ '2026-10-30 02:10:31.457021+00'),
        (151, 2559, TIMESTAMPTZ '2026-11-01 00:27:53.429162+00');
END $$;
