# Dashboards do CampusLink

O menu **Meu Dashboard** abre `/dashboard`, que direciona para `/dashboard/aluno`, `/dashboard/professor` ou `/dashboard/admin` usando o perfil da sessão.

## Organização

- `DashboardController`: valida a sessão, o perfil, a conta ativa e o acesso à turma antes de carregar a página
- `DashboardRepository`: consultas com parâmetros, contagens e agrupamentos no PostgreSQL
- `DashboardService`: organiza indicadores e monta insights com regras simples
- `DashboardFiltro`: normaliza filtros e calcula datas no fuso `America/Sao_Paulo`
- `templates/Dashboards`: três páginas e fragmentos compartilhados
- `static/css/Geral/dashboard.css` e `static/js/Geral/dashboard.js`: apresentação e gráficos

As consultas específicas são necessárias porque as listagens existentes não fornecem os agrupamentos filtrados. Nenhuma tabela, autenticação, configuração de 2FA ou regra de moderação foi alterada. As telas anteriores continuam disponíveis pelos atalhos.

## O que os números representam

### Aluno

Os vínculos vêm de `TURMAS.id_alunos`. Só entram conteúdos ativos dessas turmas. As conclusões vêm de `PROGRESSO_CONTEUDO`, sempre com o ID do aluno autenticado.

O progresso é a quantidade de conteúdos concluídos dividida pela quantidade de conteúdos disponíveis. Um conteúdo compartilhado conta uma única vez no total, mas aparece no resumo de cada turma. Sem conteúdos, o progresso é zero.

Um conteúdo é atrasado quando não foi concluído, seus eventos ativos acessíveis já passaram e ele não foi reaberto depois do último prazo. Um evento futuro em outra turma do mesmo aluno impede essa classificação, mesmo ao filtrar uma turma específica. Eventos de turmas alheias não interferem no resultado.

Não há registro persistido de sessões efetivamente estudadas. O gráfico de evolução conta conteúdos concluídos por mês, pela data `concluido_em`. A duração estimada de um material não é apresentada como horas estudadas. Um evento passado não é considerado uma atividade concluída.

### Professor

As turmas usam `id_proprietario` e `id_professores`. O total de alunos conta pessoas distintas entre as turmas. As publicações são de autoria do professor, inclusive materiais removidos; eventos são os ativos de sua autoria nas turmas às quais está vinculado.

Os conteúdos suspensos incluem os que aguardam revisão. No gráfico, as categorias são separadas: ativos, suspensos sem revisão, em análise e removidos. “Em análise” exige `status = 'suspenso_denuncia'` e `revisao_solicitada = true`.

### Administrador

As contas vêm de `USUARIOS`, com os perfis `ALUNOS`, `PROFESSORES` e `ADMINISTRADOR`. O campo `status` é booleano: inatividade não significa bloqueio ou suspensão.

As denúncias analisadas têm status `analisada_liberada` ou `analisada_removida`. O insight de liberação conta conteúdos distintos com denúncias analisadas e não afirma que esses conteúdos continuam ativos. Não há uma data de decisão que permita apresentar liberações por data da análise.

## Filtros

| Perfil | Período | Outros filtros |
| --- | --- | --- |
| Aluno | Data dos eventos e das conclusões no gráfico de evolução | Turma em todos os dados; prioridade somente em eventos |
| Professor | Publicação de conteúdos e criação de eventos | Turma; vínculos e alunos mostram a situação atual |
| Administrador | Cadastro de contas e turmas; publicação de conteúdos; criação de eventos e denúncias | Perfil somente nas informações de contas |

Os últimos 7 e 30 dias incluem hoje. “Mês atual” vai do primeiro dia até hoje. “Todos os períodos” também permite ver eventos futuros. Filtros são enviados por formulário GET; cards, gráficos, tabelas e insights são recalculados no servidor. A página explica as diferenças de escopo.

As tabelas mostram até 10 usuários, conteúdos e eventos, ou 20 turmas, com links para as listagens completas. Os gráficos por turma mostram até 10 turmas, e os limites estão indicados na interface. Os totais não são limitados pelas tabelas.

## Segurança e apresentação

Não existe endpoint separado de dados. Cada página valida o perfil exato, o tipo do objeto de login e o e-mail da sessão; depois confirma no banco o vínculo de perfil e a conta ativa. Administradores não recebem acesso aos dashboards individuais. Uma turma alheia produz HTTP 403, mesmo quando informada diretamente na URL. O identificador da pessoa nunca vem dos parâmetros da requisição.

Os gráficos usam Chart.js 4.5.1 pelo CDN. Seus dados são lidos de tabelas renderizadas e escapadas pelo Thymeleaf. As tabelas continuam acessíveis se JavaScript ou CDN falharem. Há mensagens de ausência de dados, suporte a movimento reduzido e layout em grid com ajustes para telas menores.

## Validação

Executar na pasta `CampusLink`:

```text
mvnw.cmd -Dspring.jpa.hibernate.ddl-auto=none test
node src/test/js/dashboard.test.mjs
```

Se o wrapper não estiver disponível, usar o Maven instalado com os mesmos argumentos. Os testes Java usam as configurações locais do projeto; não versionar o arquivo `.env`.

`DashboardRepositoryTests` copia apenas a estrutura real para tabelas temporárias da conexão e descarta essas tabelas ao terminar. Os dados dos testes não são inseridos nas tabelas reais. Os testes conferem isolamento entre usuários, filtros, contagens, datas, moderação, conteúdos compartilhados, navbar e renderização das três páginas, inclusive vazias.

Os testes JavaScript verificam os três tipos de gráfico, valores, ausência de dados, movimento reduzido e falha da biblioteca. Eles não substituem a inspeção visual em um navegador. A revisão visual de desktop e celular ficou pendente neste ambiente, pois o acesso ao navegador e à interface do VS Code não estava disponível.

Na validação de 02/11/2026, passaram os 65 testes Java da suíte completa, sem falhas ou testes ignorados, e os 7 cenários JavaScript. O CDN da versão fixada do Chart.js respondeu com HTTP 200.

Os commits foram feitos pelo Git local na branch existente, sem push nem PR.
