-- massa de teste para a apresentacao do tcc
-- tudo se resume em tres contas: uma aluna, uma professora e uma administradora
-- os demais usuarios so preenchem as tabelas (emails @exemplo.com, nao servem para entrar)
--
-- ANTES DE RODAR
--   1. rode o 01_limpar_banco.sql
--   2. troque 'TROQUE_AQUI' (linha v_email_base) pelo inicio do seu gmail em minusculas, ex.: 'eddiealencarcosta'
--      as tres contas viram seuemail+aluna@gmail.com, seuemail+professora@gmail.com e seuemail+admin@gmail.com
--      e os codigos do 2fa das tres chegam na sua caixa de entrada
--   3. troque 'DEFINA_A_SENHA' (linha v_senha_demo) pela senha das contas de demonstracao
--      precisa ter maiuscula, minuscula, numero e simbolo, como o sistema exige no cadastro
--      a senha nao fica gravada no repositorio, so no banco (criptografada com bcrypt)
--   4. rode no dia da apresentacao: as datas sao calculadas a partir de hoje
--
-- ANA SOUZA (aluna)  seuemail+aluna@gmail.com
--   termos      aceitou so a versao antiga: ao entrar ve o aviso e aceita a versao nova ao vivo
--               (para pular isso troque v_mostrar_aceite_termos para false)
--   riscos      Prova de Estatistica amanha ......... vermelho (12 h de estudo para 1 dia)
--               Entrega de requisitos em 4 dias ..... verde
--               P1 de Banco de Dados em 7 dias ...... amarelo (as horas livres nao bastam)
--               Apresentacao web em 12 dias ......... concluido
--               Lista 1 vencida ha 5 dias ........... atrasado
--               Indices e desempenho ................ sem evento
--   plano       horas livres hoje e nos proximos dias, o plano distribui e sugere horas
--   score       uma turma em cada situacao
--   denuncia    denunciou a Lista extra de modelagem (suspensa, aguardando o admin)
--   chamados    um aberto, um respondido pelo admin com resposta dela e um concluido
--
-- RENATA OLIVEIRA (professora)  seuemail+professora@gmail.com
--   turmas      dona de Banco de Dados, Engenharia de Software e Programacao Web; co-professora de Estatistica
--   prazo       Modelo Entidade-Relacionamento com prazo encerrado: botao re-disponibilizar
--   varias      Boas praticas com Git aparece em Banco de Dados e Engenharia de Software (meus conteudos)
--   denuncia    Lista extra de modelagem suspensa: ela pode solicitar analise
--   removido    Video de revisao antigo removido pelo admin com comentario
--   sites       khanacademy e mozilla liberados, w3schools aguardando analise, jogosonline recusado
--   acompanha   Banco de Dados com 19 alunos em situacoes diferentes
--
-- PATRICIA ALVES (administradora master)  seuemail+admin@gmail.com
--   denuncias   Lista extra de modelagem com denuncias pendentes e pedido de analise
--   sites       pedido do w3schools para liberar ou recusar
--   chamados    abertos, respondidos e concluidos
--   usuarios    alunos, professores e admins de preenchimento; Sofia Ribeiro desativada para reativar
--   logs        sessoes encerradas da Ana e da Renata
--   painel      dashboards com 6 turmas, 35 usuarios e dezenas de conteudos e eventos

BEGIN;

DO $$
DECLARE
    v_email_base text := 'teste';
    v_email_dominio text := 'gmail.com';
    v_mostrar_aceite_termos boolean := true;
    v_senha_demo text := 'DEFINA_A_SENHA';

    v_senha text;
    v_termo bigint; v_hash text;
    v_termo_antigo bigint; v_hash_antigo text;
    v_hoje date := (now() AT TIME ZONE 'America/Sao_Paulo')::date;

    -- contas principais
    u_ana integer; u_renata integer; u_patricia integer;
    a_ana bigint; p_renata bigint;
    -- preenchimento
    v_alunos bigint[]; v_usuarios_alunos integer[];
    p_carlos bigint; p_juliana bigint; p_marcos bigint; p_paula bigint;
    -- turmas
    t_bd bigint; t_es bigint; t_est bigint; t_web bigint; t_calc bigint; t_redes bigint;
    -- conteudos
    c_mer bigint; c_norm bigint; c_sql bigint; c_projeto bigint; c_indices bigint; c_suspenso bigint; c_removido bigint;
    c_git bigint; c_requisitos bigint; c_casos bigint; c_distrib bigint; c_hipotese bigint; c_regressao bigint;
    c_html bigint; c_limites bigint; c_derivadas bigint; c_osi bigint;
    -- eventos
    e_lista bigint; e_estatistica bigint; e_requisitos bigint; e_p1 bigint; e_web bigint; e_calc bigint; e_redes bigint;
    -- chamados
    ch bigint;
BEGIN
    -- exigir email real garante que os codigos do 2fa cheguem durante a apresentacao
    IF v_email_base = 'TROQUE_AQUI' THEN
        RAISE EXCEPTION 'Troque TROQUE_AQUI pelo inicio do seu gmail antes de rodar.';
    END IF;
    -- exigir senha definida evita deixar uma senha conhecida escrita no repositorio
    IF v_senha_demo = 'DEFINA_A_SENHA' OR v_senha_demo !~ '[A-Z]' OR v_senha_demo !~ '[a-z]' OR v_senha_demo !~ '[0-9]' OR v_senha_demo !~ '[^A-Za-z0-9]' THEN
        RAISE EXCEPTION 'Defina em v_senha_demo uma senha com maiuscula, minuscula, numero e simbolo antes de rodar.';
    END IF;

    SELECT id, hash_sha256 INTO v_termo, v_hash FROM public."TERMOS_VERSOES" ORDER BY id DESC LIMIT 1;
    SELECT id, hash_sha256 INTO v_termo_antigo, v_hash_antigo FROM public."TERMOS_VERSOES" WHERE id < v_termo ORDER BY id DESC LIMIT 1;
    v_senha := extensions.crypt(v_senha_demo, extensions.gen_salt('bf', 10));

    -- ===================== contas principais =====================

    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status)
    VALUES ('Ana Souza', v_email_base || '+aluna@' || v_email_dominio, '2003-04-12', v_senha, 'ALUNOS', true) RETURNING id INTO u_ana;
    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status)
    VALUES ('Renata Oliveira', v_email_base || '+professora@' || v_email_dominio, '1984-05-21', v_senha, 'PROFESSORES', true) RETURNING id INTO u_renata;
    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status)
    VALUES ('Patricia Alves', v_email_base || '+admin@' || v_email_dominio, '1990-08-09', v_senha, 'ADMINISTRADOR', true) RETURNING id INTO u_patricia;

    INSERT INTO public."ALUNOS"(rgm, id_usuario) VALUES ('11251400001', u_ana) RETURNING id INTO a_ana;
    INSERT INTO public."PROFESSORES"(matricula, id_usuario) VALUES ('20240000001', u_renata) RETURNING id INTO p_renata;
    INSERT INTO public."ADMINISTRADOR"(matricula, id_usuario, master) VALUES ('ADM0000001', u_patricia, true);

    -- aceitar so a versao antiga faz a Ana ver o aviso de termos e aceitar a nova durante a apresentacao
    IF v_mostrar_aceite_termos AND v_termo_antigo IS NOT NULL THEN
        INSERT INTO public."CONSENTIMENTOS"(id_usuario, tipo, id_termo, hash_sha256, aceito_em)
        VALUES (u_ana, 'termos_uso', v_termo_antigo, v_hash_antigo, now() - interval '60 days');
    ELSE
        INSERT INTO public."CONSENTIMENTOS"(id_usuario, tipo, id_termo, hash_sha256) VALUES (u_ana, 'termos_uso', v_termo, v_hash);
    END IF;
    INSERT INTO public."CONSENTIMENTOS"(id_usuario, tipo, id_termo, hash_sha256) VALUES (u_renata, 'termos_uso', v_termo, v_hash);

    -- ===================== usuarios de preenchimento =====================

    -- 24 alunos com nomes comuns, a ultima fica desativada para o admin reativar
    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status, created_at)
    SELECT n.nome, 'demo.aluno' || lpad(n.g::text, 2, '0') || '@exemplo.com',
           date '2001-01-15' + (n.g::int * 97), v_senha, 'ALUNOS', n.g <> 24, (now() - (n.g * 3 || ' days')::interval)::date
    FROM unnest(ARRAY['Lucas Almeida','Mariana Barbosa','Gabriel Cardoso','Beatriz Dias','Rafael Esteves','Larissa Freitas',
                      'Thiago Gomes','Camila Henriques','Pedro Ivo Martins','Júlia Lacerda','Mateus Moreira','Isabela Nunes',
                      'Gustavo Pereira','Letícia Queiroz','Vinícius Ramos','Amanda Teles','Leonardo Vieira','Bianca Xavier',
                      'Rodrigo Andrade','Natália Brito','Henrique Campos','Fernanda Duarte','Caio Fontes','Sofia Ribeiro'])
         WITH ORDINALITY AS n(nome, g);
    INSERT INTO public."ALUNOS"(rgm, id_usuario)
    SELECT '1125150' || lpad(row_number() OVER (ORDER BY u.id)::text, 4, '0'), u.id
    FROM public."USUARIOS" u WHERE u.email LIKE 'demo.aluno%@exemplo.com';
    SELECT array_agg(a.id ORDER BY u.email), array_agg(u.id ORDER BY u.email) INTO v_alunos, v_usuarios_alunos
    FROM public."ALUNOS" a JOIN public."USUARIOS" u ON u.id = a.id_usuario WHERE u.email LIKE 'demo.aluno%@exemplo.com';

    -- 4 professores e 2 administradores comuns
    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status)
    SELECT n.nome, 'demo.prof' || n.g || '@exemplo.com', date '1975-03-10' + (n.g::int * 400), v_senha, 'PROFESSORES', true
    FROM unnest(ARRAY['Carlos Mendes','Juliana Prado','Marcos Teixeira','Paula Nogueira']) WITH ORDINALITY AS n(nome, g);
    INSERT INTO public."PROFESSORES"(matricula, id_usuario)
    SELECT '2024100000' || substring(u.email from 'demo\.prof(\d)@'), u.id
    FROM public."USUARIOS" u WHERE u.email LIKE 'demo.prof_@exemplo.com';
    SELECT p.id INTO p_carlos FROM public."PROFESSORES" p JOIN public."USUARIOS" u ON u.id = p.id_usuario WHERE u.email = 'demo.prof1@exemplo.com';
    SELECT p.id INTO p_juliana FROM public."PROFESSORES" p JOIN public."USUARIOS" u ON u.id = p.id_usuario WHERE u.email = 'demo.prof2@exemplo.com';
    SELECT p.id INTO p_marcos FROM public."PROFESSORES" p JOIN public."USUARIOS" u ON u.id = p.id_usuario WHERE u.email = 'demo.prof3@exemplo.com';
    SELECT p.id INTO p_paula FROM public."PROFESSORES" p JOIN public."USUARIOS" u ON u.id = p.id_usuario WHERE u.email = 'demo.prof4@exemplo.com';

    INSERT INTO public."USUARIOS"(nome, email, datanasc, senha, perfil, status)
    SELECT n.nome, 'demo.admin' || n.g || '@exemplo.com', date '1985-06-01' + (n.g::int * 300), v_senha, 'ADMINISTRADOR', true
    FROM unnest(ARRAY['Sérgio Lopes','Vanessa Moura']) WITH ORDINALITY AS n(nome, g);
    INSERT INTO public."ADMINISTRADOR"(matricula, id_usuario, master)
    SELECT 'ADM100000' || substring(u.email from 'demo\.admin(\d)@'), u.id, false
    FROM public."USUARIOS" u WHERE u.email LIKE 'demo.admin_@exemplo.com';

    -- preenchimento aceita os termos atuais para nao aparecer aviso nas contas de apoio
    INSERT INTO public."CONSENTIMENTOS"(id_usuario, tipo, id_termo, hash_sha256)
    SELECT u.id, 'termos_uso', v_termo, v_hash FROM public."USUARIOS" u
    WHERE u.email LIKE 'demo.aluno%@exemplo.com' OR u.email LIKE 'demo.prof_@exemplo.com';

    -- ===================== sites =====================

    INSERT INTO public."DOMINIOS"(dominio, status, id_professor, justificativa, comentario_admin, analisado_em)
    SELECT d.dominio, 'aprovado', p_renata, d.justificativa, d.comentario, now() - (d.dias || ' days')::interval
    FROM (VALUES ('khanacademy.org', 'Exercícios gratuitos de banco de dados e estatística.', 'Site educacional confiável.', 20),
                 ('developer.mozilla.org', 'Documentação oficial de HTML, CSS e JavaScript.', 'Documentação de referência.', 15))
         AS d(dominio, justificativa, comentario, dias)
    WHERE NOT EXISTS (SELECT 1 FROM public."DOMINIOS" x WHERE x.dominio = d.dominio);
    INSERT INTO public."DOMINIOS"(dominio, status)
    SELECT d, 'aprovado' FROM unnest(ARRAY['youtube.com', 'youtu.be']) d
    WHERE NOT EXISTS (SELECT 1 FROM public."DOMINIOS" x WHERE x.dominio = d);
    INSERT INTO public."DOMINIOS"(dominio, status, id_professor, justificativa)
    VALUES ('w3schools.com', 'pendente', p_renata, 'Tutoriais com exemplos interativos para a turma de Programação Web.');
    INSERT INTO public."DOMINIOS"(dominio, status, id_professor, justificativa, comentario_admin, analisado_em)
    VALUES ('jogosonline.com.br', 'rejeitado', p_renata, 'Usar um jogo como exemplo em sala.', 'Site sem relação com conteúdo acadêmico.', now() - interval '3 days');

    -- ===================== turmas =====================

    -- a Ana esta nas quatro primeiras; a Renata e dona de tres e co-professora de Estatistica
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Banco de Dados', 'Modelagem, normalização e SQL. 5º semestre de Engenharia de Software.', p_renata,
            ARRAY[p_renata, p_carlos], ARRAY[a_ana] || v_alunos[1:18], now() - interval '40 days') RETURNING id INTO t_bd;
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Engenharia de Software', 'Requisitos, casos de uso e processos de desenvolvimento.', p_renata,
            ARRAY[p_renata, p_juliana], ARRAY[a_ana] || v_alunos[1:10], now() - interval '38 days') RETURNING id INTO t_es;
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Estatística Aplicada', 'Probabilidade, inferência e regressão.', p_marcos,
            ARRAY[p_marcos, p_renata], ARRAY[a_ana] || v_alunos[8:20], now() - interval '35 days') RETURNING id INTO t_est;
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Programação Web', 'HTML, CSS e JavaScript do zero ao projeto final.', p_renata,
            ARRAY[p_renata, p_paula], ARRAY[a_ana] || v_alunos[12:24], now() - interval '30 days') RETURNING id INTO t_web;
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Cálculo I', 'Limites, derivadas e aplicações.', p_carlos,
            ARRAY[p_carlos], v_alunos[1:15], now() - interval '45 days') RETURNING id INTO t_calc;
    INSERT INTO public."TURMAS"(nome_turma, descricao, id_proprietario, id_professores, id_alunos, created_at)
    VALUES ('Redes de Computadores', 'Modelo OSI, TCP/IP e protocolos de aplicação.', p_paula,
            ARRAY[p_paula], v_alunos[10:23], now() - interval '28 days') RETURNING id INTO t_redes;

    -- vinculos tambem nas tabelas de participacao, como o sistema grava ao adicionar pessoas
    INSERT INTO public."ALUNO_TURMA"(id_aluno, id_turma, situacao_aluno)
    SELECT x, t.id, 'verde' FROM public."TURMAS" t, unnest(t.id_alunos) x;
    INSERT INTO public."PROFESSOR_TURMA"(id_professor, id_turma)
    SELECT x, t.id FROM public."TURMAS" t, unnest(t.id_professores) x;

    -- ===================== conteudos =====================

    -- banco de dados
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'Modelo Entidade-Relacionamento', 'Videoaula sobre entidades, atributos e cardinalidade.', 'link',
            'https://www.youtube.com/watch?v=Q_KTYFgvu1s', '1 hora', 60, 'alta', now() - interval '20 days') RETURNING id INTO c_mer;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'Normalização até a 3FN', 'Leia o resumo e resolva os exemplos de 1FN, 2FN e 3FN.', 'texto',
            NULL, '1:30:00', 90, 'alta', now() - interval '12 days') RETURNING id INTO c_norm;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'SQL: SELECT, JOIN e agrupamentos', 'Exercícios guiados de consultas no Khan Academy.', 'link',
            'https://www.khanacademy.org/computing/computer-programming/sql', '2 horas', 120, 'media', now() - interval '10 days') RETURNING id INTO c_sql;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'Estudo de caso: banco de uma biblioteca', 'Modele e normalize o banco de uma biblioteca universitária.', 'texto',
            NULL, '3 horas', 180, 'media', now() - interval '9 days') RETURNING id INTO c_projeto;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd], p_carlos, 'Índices e desempenho de consultas', 'Material extra, sem prova associada.', 'texto',
            NULL, '45 min', 45, 'baixa', now() - interval '6 days') RETURNING id INTO c_indices;
    -- suspenso por denuncia, sem pedido de analise: a Renata pode pedir durante a apresentacao
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, status, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'Lista extra de modelagem', 'Lista de exercícios complementares.', 'link',
            'https://www.youtube.com/watch?v=ztHopE5Wnpc', '30 min', 30, 'media', 'suspenso_denuncia', now() - interval '8 days') RETURNING id INTO c_suspenso;
    -- removido pelo admin depois de denuncia
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, status, comentario_admin, created_at)
    VALUES (ARRAY[t_bd], p_renata, 'Vídeo de revisão antigo', 'Revisão gravada no semestre passado.', 'link',
            'https://www.youtube.com/watch?v=HXV3zeQKqGY', '40 min', 40, 'baixa', 'removido', 'Removido após denúncia: o vídeo trata de outro assunto.', now() - interval '25 days') RETURNING id INTO c_removido;
    -- o mesmo conteudo em duas turmas
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_bd, t_es], p_renata, 'Boas práticas de versionamento com Git', 'Commits pequenos, branches e pull requests. Vale para as duas turmas.', 'link',
            'https://www.youtube.com/watch?v=RGOj5yH7evk', '30 min', 30, 'media', now() - interval '9 days') RETURNING id INTO c_git;

    -- engenharia de software
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_es], p_renata, 'Requisitos funcionais e não funcionais', 'Diferenças, exemplos e como escrever bons requisitos.', 'texto',
            NULL, '1 hora', 60, 'alta', now() - interval '9 days') RETURNING id INTO c_requisitos;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_es], p_renata, 'Diagrama de casos de uso', 'Atores, casos de uso e relacionamentos de inclusão e extensão.', 'link',
            'https://www.youtube.com/watch?v=OkC7HKtiZC0', '1 hora', 60, 'media', now() - interval '8 days') RETURNING id INTO c_casos;

    -- estatistica: 12 horas para uma prova amanha
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_est], p_marcos, 'Distribuições de probabilidade', 'Binomial, Poisson e Normal com exercícios.', 'link',
            'https://www.khanacademy.org/math/statistics-probability', '4 horas', 240, 'alta', now() - interval '7 days') RETURNING id INTO c_distrib;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_est], p_marcos, 'Testes de hipótese', 'Hipótese nula, nível de significância e p-valor.', 'texto',
            NULL, '4 horas', 240, 'alta', now() - interval '6 days') RETURNING id INTO c_hipotese;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_est], p_marcos, 'Regressão linear simples', 'Reta de mínimos quadrados e interpretação dos coeficientes.', 'texto',
            NULL, '4 horas', 240, 'media', now() - interval '5 days') RETURNING id INTO c_regressao;

    -- programacao web
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_web], p_renata, 'HTML e CSS do zero', 'Guia da MDN para montar a primeira página.', 'link',
            'https://developer.mozilla.org/pt-BR/docs/Learn/Getting_started_with_the_web', '1 hora', 60, 'media', now() - interval '10 days') RETURNING id INTO c_html;

    -- turmas de preenchimento
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_calc], p_carlos, 'Limites e continuidade', 'Aulas e exercícios de limites.', 'link',
            'https://www.khanacademy.org/math/calculus-1', '1:30:00', 90, 'alta', now() - interval '14 days') RETURNING id INTO c_limites;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_calc], p_carlos, 'Derivadas e regras de derivação', 'Regra da cadeia, produto e quociente.', 'texto',
            NULL, '2 horas', 120, 'media', now() - interval '11 days') RETURNING id INTO c_derivadas;
    INSERT INTO public."CONTEUDOS"(id_turma, id_professor, titulo, descricao, tipo, url, duracao_estimada, duracao_estimada_min, prioridade, created_at)
    VALUES (ARRAY[t_redes], p_paula, 'Modelo OSI em 7 camadas', 'Videoaula sobre as camadas do modelo OSI.', 'link',
            'https://www.youtube.com/watch?v=vv4y_uOneC0', '1 hora', 60, 'media', now() - interval '12 days') RETURNING id INTO c_osi;

    -- ===================== eventos =====================

    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_bd, p_renata, 'Lista 1 - Modelagem', 'TRABALHO', 'Entrega da lista de modelagem ER.',
            (v_hoje - 5) + time '23:00', ((v_hoje - 5) + time '23:59') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA', now() - interval '18 days')
    RETURNING id INTO e_lista;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_est, p_marcos, 'Prova de Estatística', 'PROVA', 'Probabilidade, testes de hipótese e regressão.',
            (v_hoje + 1) + time '19:00', ((v_hoje + 1) + time '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA', now() - interval '7 days')
    RETURNING id INTO e_estatistica;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_es, p_renata, 'Entrega do documento de requisitos', 'TRABALHO', 'Documento com requisitos e diagrama de casos de uso.',
            (v_hoje + 4) + time '23:00', ((v_hoje + 4) + time '23:59') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA', now() - interval '9 days')
    RETURNING id INTO e_requisitos;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_bd, p_renata, 'P1 - Banco de Dados', 'PROVA', 'Prova sobre normalização, SQL e modelagem.',
            (v_hoje + 7) + time '19:00', ((v_hoje + 7) + time '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA', now() - interval '12 days')
    RETURNING id INTO e_p1;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_web, p_renata, 'Apresentação do projeto web', 'APRESENTACAO', 'Apresentação da primeira página publicada.',
            (v_hoje + 12) + time '19:00', ((v_hoje + 12) + time '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'BAIXA', now() - interval '10 days')
    RETURNING id INTO e_web;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_calc, p_carlos, 'P1 de Cálculo', 'PROVA', 'Limites e derivadas.',
            (v_hoje + 9) + time '19:00', ((v_hoje + 9) + time '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'ALTA', now() - interval '14 days')
    RETURNING id INTO e_calc;
    INSERT INTO public."EVENTOS"(id_turma, id_professor, titulo, tipo, descricao, data_hora, data_fim, status, prioridade, created_at)
    VALUES (t_redes, p_paula, 'Seminário de redes', 'APRESENTACAO', 'Apresentação em grupo sobre uma camada do modelo OSI.',
            (v_hoje + 14) + time '19:00', ((v_hoje + 14) + time '21:00') AT TIME ZONE 'America/Sao_Paulo', 'ATIVO', 'MEDIA', now() - interval '12 days')
    RETURNING id INTO e_redes;

    INSERT INTO public."EVENTO_CONTEUDO"(id_evento, id_conteudo) VALUES
        (e_lista, c_mer),
        (e_estatistica, c_distrib), (e_estatistica, c_hipotese), (e_estatistica, c_regressao),
        (e_requisitos, c_requisitos), (e_requisitos, c_casos), (e_requisitos, c_git),
        (e_p1, c_norm), (e_p1, c_sql), (e_p1, c_projeto),
        (e_web, c_html),
        (e_calc, c_limites), (e_calc, c_derivadas),
        (e_redes, c_osi);

    -- ===================== progresso e horas livres da Ana =====================

    -- Ana estudou normalizacao e terminou o conteudo da apresentacao web; nao entregou a lista 1
    INSERT INTO public."PROGRESSO_CONTEUDO"(id_aluno, id_conteudo, concluido_em) VALUES
        (a_ana, c_norm, now() - interval '2 days'),
        (a_ana, c_html, now() - interval '1 day');

    -- 6 horas hoje e nos dias 2 e 3, nada nos dias 5 e 6 e 2 horas por dia depois da P1
    -- com isso: amanha vermelho, dia 4 verde, dia 7 amarelo
    INSERT INTO public."DISPONIBILIDADES"(id_aluno, data, horas_disponiveis)
    SELECT a_ana, v_hoje + d, 6 FROM unnest(ARRAY[0, 2, 3]) d;
    INSERT INTO public."DISPONIBILIDADES"(id_aluno, data, horas_disponiveis)
    SELECT a_ana, v_hoje + d, 2 FROM generate_series(8, 11) d;

    -- ===================== progresso e horas do preenchimento =====================

    -- cada aluno de apoio concluiu uma parte diferente para o acompanhamento mostrar todas as situacoes
    INSERT INTO public."PROGRESSO_CONTEUDO"(id_aluno, id_conteudo, concluido_em)
    SELECT v_alunos[g], c.id, now() - ((g % 9 + 1) || ' days')::interval
    FROM generate_series(1, 24) g,
         (VALUES (c_mer, 2), (c_norm, 3), (c_sql, 4), (c_projeto, 6), (c_git, 2), (c_requisitos, 3), (c_casos, 4),
                 (c_distrib, 2), (c_hipotese, 3), (c_html, 2), (c_limites, 2), (c_derivadas, 3), (c_osi, 2)) AS c(id, divisor)
    WHERE g % c.divisor = 0
      AND EXISTS (SELECT 1 FROM public."TURMAS" t WHERE v_alunos[g] = ANY(t.id_alunos)
                  AND t.id = ANY((SELECT id_turma FROM public."CONTEUDOS" WHERE id = c.id)::bigint[]));

    INSERT INTO public."DISPONIBILIDADES"(id_aluno, data, horas_disponiveis)
    SELECT v_alunos[g], v_hoje + d, 1 + (g + d) % 4
    FROM generate_series(1, 24) g, generate_series(0, 13) d
    WHERE g % 4 <> 0;

    -- ===================== denuncias =====================

    -- a Ana e mais dois alunos denunciaram a lista extra, que ficou suspensa esperando o admin
    INSERT INTO public."DENUNCIAS"(id_conteudo, id_aluno, id_professor, motivo, status, created_at) VALUES
        (c_suspenso, a_ana, p_renata, 'O link abre um vídeo que não tem relação com a lista de modelagem.', 'pendente', now() - interval '2 days'),
        (c_suspenso, v_alunos[3], p_renata, 'Vídeo fora do tema da lista.', 'pendente', now() - interval '2 days'),
        (c_suspenso, v_alunos[7], p_renata, 'O conteúdo não abre a lista de exercícios.', 'pendente', now() - interval '1 day'),
        (c_removido, v_alunos[5], p_renata, 'O vídeo é de outra disciplina.', 'analisada_removida', now() - interval '20 days'),
        (c_osi, v_alunos[12], p_paula, 'Achei que o link estava fora do ar.', 'analisada_liberada', now() - interval '4 days');

    -- ===================== chamados =====================

    -- Ana: aberto
    INSERT INTO public."CHAMADOS"(id_usuario, assunto, descricao, status, created_at)
    VALUES (u_ana, 'Não consigo abrir o material de SQL', 'Quando clico em Abrir Link no conteúdo de SQL a página demora para carregar. Podem verificar?', 'aberto', now() - interval '3 hours');
    -- Ana: respondido pelo admin e com resposta dela
    INSERT INTO public."CHAMADOS"(id_usuario, assunto, descricao, status, created_at)
    VALUES (u_ana, 'Dúvida sobre o plano de estudos', 'O plano sugere horas em dias que eu não cadastrei. É normal?', 'respondido', now() - interval '2 days')
    RETURNING id INTO ch;
    INSERT INTO public."CHAMADO_RESPOSTAS"(id_chamado, id_admin_usuario, resposta, tipo_autor, created_at)
    VALUES (ch, u_patricia, 'Olá, Ana! Sim: quando as horas livres não bastam, o plano sugere horas extras. Cadastre mais horas para o risco diminuir.', 'admin', now() - interval '1 day 20 hours');
    INSERT INTO public."CHAMADO_RESPOSTAS"(id_chamado, id_usuario, resposta, tipo_autor, created_at)
    VALUES (ch, u_ana, 'Entendi, obrigada! Vou cadastrar mais horas.', 'usuario', now() - interval '1 day 18 hours');
    -- Ana: concluido
    INSERT INTO public."CHAMADOS"(id_usuario, assunto, descricao, status, created_at, concluido_em, id_admin_conclusao)
    VALUES (u_ana, 'Como recebo uma cópia dos meus dados?', 'Quero saber quais dados meus estão guardados no CampusLink.', 'concluido',
            now() - interval '15 days', now() - interval '14 days', u_patricia) RETURNING id INTO ch;
    INSERT INTO public."CHAMADO_RESPOSTAS"(id_chamado, id_admin_usuario, resposta, tipo_autor, created_at)
    VALUES (ch, u_patricia, 'Acesse Meu perfil e peça a cópia dos seus dados: ela chega no seu e-mail. Qualquer dúvida, é só abrir outro chamado.', 'admin', now() - interval '14 days 2 hours');

    -- preenchimento: chamados de outros usuarios em situacoes variadas
    INSERT INTO public."CHAMADOS"(id_usuario, assunto, descricao, status, created_at, concluido_em, id_admin_conclusao)
    SELECT v_usuarios_alunos[g], 'Dúvida ' || g || ' sobre a plataforma', 'Chamado de apoio número ' || g || ' para preencher a lista do administrador.',
           (ARRAY['aberto','respondido','concluido'])[1 + g % 3], now() - (g * 7 || ' hours')::interval,
           CASE WHEN g % 3 = 2 THEN now() - (g * 7 - 2 || ' hours')::interval END,
           CASE WHEN g % 3 = 2 THEN u_patricia END
    FROM generate_series(1, 8) g;
    INSERT INTO public."CHAMADO_RESPOSTAS"(id_chamado, id_admin_usuario, resposta, tipo_autor)
    SELECT c.id, u_patricia, 'Obrigado pelo contato! Sua solicitação foi verificada.', 'admin'
    FROM public."CHAMADOS" c WHERE c.status <> 'aberto' AND c.id_usuario = ANY(v_usuarios_alunos);

    -- ===================== atividade recente e logs =====================

    INSERT INTO public."TURMA_ATIVIDADE"(id_usuario, id_turma, alterado_em) VALUES
        (u_renata, t_bd, now() - interval '1 hour'),
        (u_renata, t_es, now() - interval '1 day'),
        (u_renata, t_web, now() - interval '2 days'),
        (u_ana, t_est, now() - interval '30 minutes'),
        (u_ana, t_bd, now() - interval '3 hours');

    -- sessoes encerradas da Ana e da Renata para a consulta de logs do admin
    INSERT INTO public."LOGS_SESSOES"(sessao_log_id, usuario_id, inicio, fim, status, log_completo, historico_gerado_em) VALUES
        (gen_random_uuid(), u_ana, now() - interval '1 day 2 hours', now() - interval '1 day 1 hour 35 minutes', 'ENCERRADA',
         to_char(now() - interval '1 day 2 hours', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Login concluído. perfil=aluno doisFatores=true' || chr(10) ||
         to_char(now() - interval '1 day 1 hour 50 minutes', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Conteúdo concluído na turma Programação Web' || chr(10) ||
         to_char(now() - interval '1 day 1 hour 35 minutes', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Sessão encerrada pelo usuário',
         now() - interval '1 day 1 hour 35 minutes'),
        (gen_random_uuid(), u_renata, now() - interval '5 hours', now() - interval '4 hours', 'ENCERRADA',
         to_char(now() - interval '5 hours', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Login concluído. perfil=professor doisFatores=true' || chr(10) ||
         to_char(now() - interval '4 hours 30 minutes', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Conteúdo cadastrado na turma Banco de Dados' || chr(10) ||
         to_char(now() - interval '4 hours', 'YYYY-MM-DD HH24:MI:SS') || ' [INFO] Sessão expirada por inatividade',
         now() - interval '4 hours');

    RAISE NOTICE 'Massa criada. Contas: %+aluna@%, %+professora@%, %+admin@% (senha definida em v_senha_demo)',
        v_email_base, v_email_dominio, v_email_base, v_email_dominio, v_email_base, v_email_dominio;
END $$;

COMMIT;

-- conferencia: quantidade criada em cada tabela
SELECT 'usuarios' AS tabela, count(*) AS linhas FROM public."USUARIOS"
UNION ALL SELECT 'alunos', count(*) FROM public."ALUNOS"
UNION ALL SELECT 'professores', count(*) FROM public."PROFESSORES"
UNION ALL SELECT 'administradores', count(*) FROM public."ADMINISTRADOR"
UNION ALL SELECT 'turmas', count(*) FROM public."TURMAS"
UNION ALL SELECT 'conteudos', count(*) FROM public."CONTEUDOS"
UNION ALL SELECT 'eventos', count(*) FROM public."EVENTOS"
UNION ALL SELECT 'progresso', count(*) FROM public."PROGRESSO_CONTEUDO"
UNION ALL SELECT 'denuncias', count(*) FROM public."DENUNCIAS"
UNION ALL SELECT 'chamados', count(*) FROM public."CHAMADOS"
UNION ALL SELECT 'sites', count(*) FROM public."DOMINIOS"
UNION ALL SELECT 'sessoes com log', count(*) FROM public."LOGS_SESSOES";
