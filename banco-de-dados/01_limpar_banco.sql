-- limpeza total do banco do campuslink deixando apenas o essencial para o sistema funcionar
--
-- mantem:
--   TERMOS_VERSOES      todas as versoes dos termos (o banco bloqueia apagar ou alterar)
--   ADMINISTRADOR       apenas os administradores master e as contas de usuario deles
--   DOMINIOS            apenas os sites ja liberados (aprovados), sem professor vinculado
--
-- apaga todo o resto: alunos, professores, turmas, conteudos, eventos, progresso, disponibilidades,
-- denuncias, chamados, consentimentos, logs de sessao e registros de arquivos
--
-- os arquivos enviados ficam no storage do supabase e nao sao apagados por sql:
-- depois de rodar, apague-os em Storage > bucket dos arquivos > selecionar todos > delete
--
-- como usar: cole tudo no SQL Editor do Supabase e execute. tudo roda em uma transacao:
-- se algum passo falhar, nada e apagado

BEGIN;

-- conferir admin master impede deixar o sistema sem ninguem que consiga entrar no painel
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public."ADMINISTRADOR" WHERE master) THEN
        RAISE EXCEPTION 'Nenhum administrador master encontrado. A limpeza foi cancelada para nao travar o acesso.';
    END IF;
END $$;

-- sites liberados continuam valendo para todos os professores, entao perdem o vinculo com quem pediu
DELETE FROM public."DOMINIOS" WHERE status <> 'aprovado';
UPDATE public."DOMINIOS" SET id_professor = NULL;

-- tabelas sem nada a manter sao esvaziadas juntas e os contadores de id voltam ao inicio
TRUNCATE TABLE
    public."LOGS_EVENTOS",
    public."LOGS_SESSOES",
    public."CHAMADO_RESPOSTAS",
    public."CHAMADOS",
    public."DENUNCIAS",
    public."PROGRESSO_CONTEUDO",
    public."EVENTO_CONTEUDO",
    public."EVENTOS",
    public."ARQUIVOS",
    public."CONTEUDOS",
    public."DISPONIBILIDADES",
    public."ALUNO_TURMA",
    public."PROFESSOR_TURMA",
    public."TURMA_ATIVIDADE",
    public."TURMAS",
    public."CONSENTIMENTOS",
    public."ALUNOS"
RESTART IDENTITY;

-- professores sao apagados um a um porque a tabela de sites ainda aponta para eles e impede o truncate
DELETE FROM public."PROFESSORES";
SELECT setval(pg_get_serial_sequence('public."PROFESSORES"', 'id'), 1, false);

-- usuarios e administradores comuns saem, os master ficam
DELETE FROM public."ADMINISTRADOR" WHERE NOT master;
DELETE FROM public."USUARIOS" u
WHERE NOT EXISTS (SELECT 1 FROM public."ADMINISTRADOR" a WHERE a.id_usuario = u.id);

COMMIT;

-- conferencia: as tabelas de dados devem voltar zero e as essenciais mostram o que ficou
SELECT 'USUARIOS (admins master)' AS tabela, count(*) AS linhas FROM public."USUARIOS"
UNION ALL SELECT 'ADMINISTRADOR', count(*) FROM public."ADMINISTRADOR"
UNION ALL SELECT 'TERMOS_VERSOES', count(*) FROM public."TERMOS_VERSOES"
UNION ALL SELECT 'DOMINIOS (sites liberados)', count(*) FROM public."DOMINIOS"
UNION ALL SELECT 'ALUNOS', count(*) FROM public."ALUNOS"
UNION ALL SELECT 'PROFESSORES', count(*) FROM public."PROFESSORES"
UNION ALL SELECT 'TURMAS', count(*) FROM public."TURMAS"
UNION ALL SELECT 'CONTEUDOS', count(*) FROM public."CONTEUDOS"
UNION ALL SELECT 'EVENTOS', count(*) FROM public."EVENTOS"
UNION ALL SELECT 'CHAMADOS', count(*) FROM public."CHAMADOS"
UNION ALL SELECT 'LOGS_SESSOES', count(*) FROM public."LOGS_SESSOES";
