-- indices de desempenho das telas do campuslink
--
-- as listas do administrador, do professor e do aluno filtram e ordenam por colunas que nao tinham indice,
-- entao o banco percorria a tabela inteira a cada pagina. estes indices cobrem os filtros, juncoes e ordenacoes usados.
--
-- como usar: cole no SQL Editor do Supabase e execute. pode rodar mais de uma vez (IF NOT EXISTS)
-- e nao apaga nem altera dados.

-- participacao nas turmas: contagem de alunos e professores por turma e turmas de cada pessoa
CREATE INDEX IF NOT EXISTS idx_aluno_turma_turma ON public."ALUNO_TURMA" (id_turma);
CREATE INDEX IF NOT EXISTS idx_aluno_turma_aluno ON public."ALUNO_TURMA" (id_aluno);
CREATE INDEX IF NOT EXISTS idx_professor_turma_turma ON public."PROFESSOR_TURMA" (id_turma);
CREATE INDEX IF NOT EXISTS idx_professor_turma_professor ON public."PROFESSOR_TURMA" (id_professor);

-- perfis ligados ao usuario: toda lista de usuarios junta essas tabelas pelo id_usuario
CREATE INDEX IF NOT EXISTS idx_alunos_usuario ON public."ALUNOS" (id_usuario);
CREATE INDEX IF NOT EXISTS idx_professores_usuario ON public."PROFESSORES" (id_usuario);
CREATE INDEX IF NOT EXISTS idx_administrador_usuario ON public."ADMINISTRADOR" (id_usuario);

-- usuarios: login e buscas comparam o email em minusculas; listas ordenam por nome
CREATE INDEX IF NOT EXISTS idx_usuarios_email_lower ON public."USUARIOS" (lower(email));
CREATE INDEX IF NOT EXISTS idx_usuarios_nome ON public."USUARIOS" (nome, id);

-- turmas, conteudos e eventos
CREATE INDEX IF NOT EXISTS idx_turmas_proprietario ON public."TURMAS" (id_proprietario);
CREATE INDEX IF NOT EXISTS idx_turmas_nome ON public."TURMAS" (nome_turma, id);
CREATE INDEX IF NOT EXISTS idx_conteudos_turmas ON public."CONTEUDOS" USING gin (id_turma);
CREATE INDEX IF NOT EXISTS idx_conteudos_professor ON public."CONTEUDOS" (id_professor);
CREATE INDEX IF NOT EXISTS idx_conteudos_status_criacao ON public."CONTEUDOS" (status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_eventos_turma ON public."EVENTOS" (id_turma, data_hora);
CREATE INDEX IF NOT EXISTS idx_eventos_data ON public."EVENTOS" (data_hora DESC, id DESC);

-- chamados: a lista do admin sem filtro de situacao ordena pela data de abertura
CREATE INDEX IF NOT EXISTS idx_chamados_criacao ON public."CHAMADOS" (created_at DESC, id DESC);

-- sites e logs de sessao
CREATE INDEX IF NOT EXISTS idx_dominios_status ON public."DOMINIOS" (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_logs_sessoes_encerradas ON public."LOGS_SESSOES" (status, fim DESC);
CREATE INDEX IF NOT EXISTS idx_logs_sessoes_usuario ON public."LOGS_SESSOES" (usuario_id);

-- atualizar as estatisticas faz o banco escolher os indices novos logo em seguida
ANALYZE public."ALUNO_TURMA", public."PROFESSOR_TURMA", public."ALUNOS", public."PROFESSORES", public."ADMINISTRADOR",
        public."USUARIOS", public."TURMAS", public."CONTEUDOS", public."EVENTOS", public."DOMINIOS", public."LOGS_SESSOES", public."CHAMADOS";
