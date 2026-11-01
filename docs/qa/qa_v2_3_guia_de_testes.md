# Guia de testes v2: planejamento de estudos, risco e prazos

Branch testada: `feature/viabilidade-risco-v2`

Este guia é para quem **nunca viu o código**. Siga os cenários na ordem. Em cada um você encontra:

- **pré-condição**;
- **passo a passo**;
- **resultado esperado**, com textos e números exatos;
- **arquivos** responsáveis pelo comportamento, para anexar ao reportar bug.

---

## 0. Arquivos deste pacote (`docs/qa/`)

| Arquivo | Para que serve |
|---|---|
| `qa_v2_1_massa_de_teste.sql` | Cria a massa de teste. Pode rodar quantas vezes quiser: ele apaga e recria tudo. |
| `qa_v2_2_restaurar_estado_original.sql` | Apaga a massa e devolve o banco ao estado real (de 01/11/2026). Rode **só no fim** de tudo. |
| `qa_v2_3_guia_de_testes.md` | Este guia. |

> Os arquivos antigos `qa_cl31_*.sql` estão desatualizados. **Não use.**

Abreviações usadas na coluna "Arquivos":

- `java/` = `CampusLink/src/main/java/com/example/CampusLink/`
- `templates/` = `CampusLink/src/main/resources/templates/`
- `static/` = `CampusLink/src/main/resources/static/`

---

## 1. Preparação

### 1.1 Subir a aplicação

1. Faça checkout da branch `feature/viabilidade-risco-v2`.
2. Rode a aplicação (Spring Boot).
3. Acesse **http://localhost:8080**.

### 1.2 Criar a massa de teste

1. Abra o Supabase do projeto e vá em **SQL Editor**.
2. Cole o conteúdo de `qa_v2_1_massa_de_teste.sql` e clique em **Run**.
3. O resultado é uma tabela com 3 colunas. **Anote ou tire print dela.** Ela converte as datas do guia em datas reais:
   - `dia_relativo`: D-3 … D0 (hoje) … D+10;
   - `data_real`: a data no calendário;
   - `eventos_do_dia`: título, horário, `id_turma` e `id_evento`.
4. Pegue os IDs na coluna `eventos_do_dia`:
   - **ID_A**: o `id_turma` dos eventos "QA Prova 1", "QA Quiz passado" etc.
   - **ID_B**: o `id_turma` do evento "QA B Entrega passada".

> **IMPORTANTE: as datas são relativas ao dia em que o script rodou.**
> - Teste **no mesmo dia** em que rodou o script.
> - Se virar o dia, rode o script de novo antes de continuar.
> - Alguns cenários **alteram dados**. Eles estão marcados com 🔁. Depois deles, rode o script de massa de novo antes de seguir para o próximo cenário.

### 1.3 Contas

| Papel | E-mail | Nome que aparece na tela |
|---|---|---|
| Professor | `eddiealencarcosta@gmail.com` | eddie alencar costa |
| Aluno | `eddiealencarcosta00@gmail.com` | eddie alencar costa |

- O login (`/login`) pede um **código de verificação enviado por e-mail**. Peça o código ao Eddie na hora de logar.
- Use **duas janelas**: uma normal (professor) e uma anônima (aluno). Assim você não precisa sair e entrar toda hora.
- Existem também personagens **fictícios**, sem login. Eles só aparecem nas telas do professor:
  - alunas/alunos: **Ana Ferreira (Fictício)** e **Bruno Ferreira (Fictício)**;
  - professora: **Ana Almeida (Fictício)**.

---

## 2. Regras do sistema em 1 minuto

O sistema avisa ao aluno se ele vai conseguir estudar tudo antes de cada evento (prova, trabalho…).

- **Carga**: soma da duração dos conteúdos **não concluídos**.
  - É acumulada evento após evento: a carga da Prova final inclui tudo que veio antes dela.
  - Conteúdo sem duração cadastrada vale **1h**.
- **Capacidade**: soma das horas de disponibilidade que o aluno cadastrou. Entram os dias de **hoje até a véspera** do evento.
  - **Dias em que há qualquer evento não contam.**
- **Níveis** (selo colorido):

| Selo | Quando aparece |
|---|---|
| 🟢 **Possível** | carga ≤ capacidade |
| 🟡 **Replanejar** | carga > capacidade, mas cabe em até **8h por dia de estudo**. O texto diz quantas horas faltam cadastrar. |
| 🔴 **Impossível** | nem estudando 8h em todos os dias de estudo dá tempo |
| ✔ **Concluído** | o aluno marcou o conteúdo |
| ⚫ **Não concluído** | o evento já passou e o aluno não concluiu |
| **Sem evento próximo** | o conteúdo não está em nenhum evento futuro |

- **Eventos no mesmo dia**: são ordenados por **prioridade** (Alta, Média, Baixa) e só depois por horário.
- **Conteúdo repetido** em dois eventos conta **nos dois**.
- **Prazo do conteúdo**: termina quando o evento começa. Depois disso:
  - o aluno **não consegue mais concluir** o conteúdo, mas pode desfazer uma conclusão;
  - o professor pode clicar em **Re-disponibilizar** para reabrir o prazo. Pode fazer isso o **autor** do conteúdo ou o **dono da turma**.
- **Plano de estudos** (no calendário do aluno):
  1. Primeiro o sistema preenche as horas que o aluno cadastrou, dos primeiros dias para os últimos.
  2. O que sobrar vira **sugestão**, distribuída de forma equilibrada, até 8h por dia.
  3. O que não couber nem assim é o "faltou".

---

## 3. A massa de teste (o que o script cria)

### 3.1 Turmas

| Turma | Dono | Professores | Alunos |
|---|---|---|---|
| **[QA] Planejamento de Estudos** (ID_A) | professor Eddie | Eddie, Ana Almeida | aluno Eddie, Ana Ferreira, Bruno Ferreira |
| **[QA] Turma da Prof. Ana Almeida** (ID_B) | Ana Almeida (fictícia) | Ana Almeida, Eddie | aluno Eddie |

Os 3 alunos são **retirados** da turma 52 enquanto a massa existir. O script de restauração os devolve.

### 3.2 Eventos

| Dia | Evento | Prioridade | Conteúdos ligados |
|---|---|---|---|
| D-3 10:00 | QA Quiz passado (já passou) | Média | QA 07, QA 08, QA 12 |
| D-2 10:00 | QA B Entrega passada (turma B, já passou) | Média | QA B1, QA B2 |
| D-1 10:00 | QA Lista reaberta (já passou) | Média | QA 09 |
| D+3 19:00 | **QA Prova 1** | **Alta** | QA 01, QA 02, QA 03 |
| D+5 10:00 | **QA Trabalho 2** | **Média** | QA 04 |
| D+5 20:00 | **QA Seminário 3** | **Alta** | QA 05 |
| D+9 19:00 | **QA Prova final** | **Alta** | QA 02 (repetido), QA 06 |

### 3.3 Conteúdos

| Conteúdo | Duração | Autor | Observação |
|---|---|---|---|
| QA 01 - Introdução | 1h | Eddie | |
| QA 02 - Variáveis (em 2 eventos) | 2h | Eddie | conta na Prova 1 **e** na Prova final |
| QA 03 - Comentários (sem duração) | vazio, vale 1h | Eddie | |
| QA 04 - Laços | 4h | Eddie | |
| QA 05 - Recursão | 5h | Eddie | |
| QA 06 - Projeto final | 50h | Eddie | |
| QA 07 - Lista de exercícios (vencida) | 1h | Eddie | prazo encerrado em D-3 |
| QA 08 - Leitura dirigida (vencida e feita) | 30min | Eddie | prazo encerrado em D-3 |
| QA 09 - Lista reaberta | 45min | Eddie | evento passou, mas foi re-disponibilizado |
| QA 10 - Material complementar (sem evento) | 45min | Eddie | |
| QA 11 - Exercícios extras (sem evento) | 10h | Eddie | |
| QA 12 - Texto da Prof. Ana Almeida (vencido) | 45min | **Ana Almeida** | prazo encerrado em D-3 |
| QA B1 - Conteúdo da Prof. Ana Almeida (vencido) | 1h | **Ana Almeida** | turma B, prazo encerrado em D-2 |
| QA B2 - Conteúdo do professor Eddie (vencido) | 1h | Eddie | turma B, prazo encerrado em D-2 |

### 3.4 Quem já concluiu o quê

| Aluno | Concluiu (há quantos dias) |
|---|---|
| aluno Eddie | QA 01 (1 dia), QA 11 (2 dias), QA 08 (10 dias) |
| Ana Ferreira | QA 01 e QA 02 (1 dia); QA 07, QA 08 e QA 12 (4 dias) |
| Bruno Ferreira | QA 08 (5 dias) |

### 3.5 Disponibilidade do aluno Eddie

| D0 | D+1 | D+2 | D+3 | D+4 | D+5 | D+6 | D+7 | D+8 |
|---|---|---|---|---|---|---|---|---|
| 2h | 2h | 1h | 4h ⚠ | 2h | — | 1h | 1h | 1h |

⚠ D+3 é dia da Prova 1. Essas 4h **não podem** ser contadas em nenhum cálculo.

Ana e Bruno não têm disponibilidade nenhuma.

---

## 4. Cenários

Convenção: "D+4" significa a data real da linha D+4 na tabela do passo 1.2. Na tela, datas aparecem como **dd/MM**.

### Bloco A: aluno, página Score (`/score`)

#### A1. Resumo geral do aluno
- **Login:** aluno.
- **Passos:**
  1. Clique em **Score** no menu, ou acesse `http://localhost:8080/score`.
- **Esperado:**
  - **Ritmo de estudos:**
    - "Nos últimos 7 dias você concluiu **11h** de estudo (média de **1h34** por dia)."
      - 11h = QA 01 (1h) + QA 11 (10h). QA 08 foi há 10 dias e fica fora.
    - "Para **QA Prova 1 · D+3** você precisa de cerca de **1h** por dia."
    - Selo verde **No ritmo**.
  - **Prioridades**: exatamente 4 itens, nesta ordem (primeiro os Impossíveis, depois os Replanejar):
    1. **QA 06 - Projeto final**, "[QA] Planejamento de Estudos · QA Prova final · D+9", selo **Impossível**. Motivo: "Faltam 64h de estudo e você tem 10h disponíveis até D+9. Não há tempo suficiente até o evento."
    2. **QA 05 - Recursão**, "… · QA Seminário 3 · D+5", **Replanejar**. Motivo: "Faltam 8h de estudo e você tem 7h disponíveis até D+5. Adicione 1h de disponibilidade até D+4 para ficar possível."
    3. **QA 04 - Laços**, "… · QA Trabalho 2 · D+5", **Replanejar**. Motivo: "Faltam 12h de estudo (incluindo 5h de QA Seminário 3, no mesmo dia) e você tem 7h disponíveis até D+5. Adicione 5h de disponibilidade até D+4 para ficar possível."
    4. **QA 02 - Variáveis (em 2 eventos)**, "… · QA Prova final · D+9", **Replanejar**. Motivo: "Faltam 14h de estudo e você tem 10h disponíveis até D+9. Adicione 4h de disponibilidade até D+8 para ficar possível."
  - Os conteúdos **vencidos** (QA 07, QA 12, QA B1, QA B2) **não** aparecem em Prioridades.
  - **Minhas turmas:**
    - "[QA] Planejamento de Estudos": "**3/12** conteúdos concluídos" e próximo evento "QA Prova 1 · D+3" (Possível).
    - "[QA] Turma da Prof. Ana Almeida": "**0/2** conteúdos concluídos", sem evento próximo.
- **Por que esses números?** A capacidade nunca inclui as 4h de D+3 (dia de prova) nem D+5 (dia de evento):
  - até D+5: 2+2+1+2 = **7h**;
  - até D+9: 2+2+1+2+1+1+1 = **10h**.
- **Arquivos:**
  - `java/controller/Usuarios/Aluno/ScoreController.java` (`exibirScore`, `montarPrioridades`, `montarRitmo`);
  - `java/service/ViabilidadeService.java` (`calcular`, `capacidadeAte`, `montarMotivo`);
  - `templates/Usuarios/Aluno/score.html`;
  - `templates/fragments/risco.html`.

#### A2. Detalhe da turma no Score
- **Login:** aluno.
- **Passos:**
  1. Em `/score`, clique na turma **[QA] Planejamento de Estudos**. URL: `/score/turma/ID_A`.
  2. Clique em cada evento para expandir.
- **Esperado:**
  - No topo: "**3/12 concluídos**".
  - **Próximos eventos**, nesta ordem: Prova 1 → **Seminário 3 → Trabalho 2** → Prova final.
    - O Seminário (Alta, 20:00) vem antes do Trabalho (Média, 10:00), mesmo sendo mais tarde. A prioridade manda.

| Evento | Selo | Texto do evento | Aviso ⚠ | Barra de progresso |
|---|---|---|---|---|
| QA Prova 1 · D+3 | Possível | "Faltam 3h de estudo e você tem 5h disponíveis até D+3." | nenhum | "1/3 conteúdos · 1h de 4h concluídas" |
| QA Seminário 3 · D+5 | Replanejar | igual ao item 2 do A1 | "⚠ Faltam **1h** na sua disponibilidade antes deste evento. Veja a sugestão de estudo no calendário." | "0/1 conteúdos · 0h de 5h concluídas" |
| QA Trabalho 2 · D+5 | Replanejar | igual ao item 3 do A1 (cita o Seminário 3) | "⚠ Faltam **4h** …" | "0/1 conteúdos · 0h de 4h concluídas" |
| QA Prova final · D+9 | Impossível | igual ao item 1 do A1 | "⚠ Faltam **49h** …" | "0/2 conteúdos · 0h de 52h concluídas" |

  - Conteúdos dentro dos eventos:
    - **Prova 1:** QA 01 ✔ Concluído; QA 02 Possível; QA 03 Possível.
    - **Seminário 3:** QA 05 Replanejar, com "faltou 1h".
    - **Trabalho 2:** QA 04 Replanejar, com "faltou 4h".
    - **Prova final:** QA 02 Replanejar, **sem** "faltou"; QA 06 Impossível, com "faltou 49h".
  - **Conteúdo repetido:** QA 02 aparece nos dois eventos com selos diferentes (Possível na Prova 1, Replanejar na Prova final).
  - **Conteúdos sem evento próximo:**
    - QA 07 e QA 12: **Não concluído**. Ao passar o mouse: "O prazo terminou em D-3 e este conteúdo não foi concluído."
    - QA 08 e QA 11: **Concluído**.
    - QA 09 e QA 10: **Sem evento próximo**. QA 09 foi reaberto, por isso não é "Não concluído".
- **Arquivos:**
  - `ScoreController.java` (`exibirScoreTurma`);
  - `ViabilidadeService.java` (`planejar`, `sugerirHoras`, `listarAtrasados`);
  - `templates/Usuarios/Aluno/scoreTurma.html`.

#### A3. 🔁 Cadastrar disponibilidade e o evento virar "Possível"
- **Login:** aluno.
- **Passos:**
  1. Acesse `/eventos/cadastrar`. Aparece o formulário **Cadastrar Disponibilidade**.
  2. Data = **D+4**, horas = **3**. Salve. Isso **substitui** as 2h que já existiam.
  3. Abra `/score/turma/ID_A`.
- **Esperado:**
  - Mensagem "Disponibilidade cadastrada com sucesso!".
  - **QA Seminário 3** passa a **Possível**: "Faltam 8h de estudo e você tem 8h disponíveis até D+5." O aviso ⚠ some.
  - **QA Trabalho 2** continua Replanejar, agora com "… e você tem 8h disponíveis até D+5. Adicione **4h** de disponibilidade até D+4 para ficar possível."
  - Em `/score`, QA 05 sai de Prioridades.
- **Teste extra:**
  1. Cadastre **5h** em **D+3** (dia da Prova 1).
  2. **Esperado:** nenhum número muda, porque dia de evento não conta.
- **Arquivos:**
  - `java/controller/Geral/EventosController.java` (`/cadastrar-disponibilidade`);
  - `java/service/DisponibilidadeService.java`;
  - `ViabilidadeService.java`.

### Bloco B: aluno, calendário (`/eventos`)

#### B1. Marcadores "Estudar Xh" no calendário
- **Login:** aluno.
- **Passos:**
  1. Clique em **Eventos** no menu (`/eventos`).
  2. Olhe o mês atual. Se D+9 cair no mês seguinte, avance o mês.
- **Esperado:**
  - Em **D0, D+1, D+2, D+4, D+6, D+7 e D+8** aparece o botão **"Estudar 8h"**, no estilo de **sugestão**: o dia já está no limite de 8h por causa do Projeto final.
  - Em **D+3, D+5 e D+9**, dias de evento, **não** há "Estudar".
  - Ao passar o mouse num marcador, a dica lista os conteúdos e termina com "Inclui Xh sugeridas: cadastre disponibilidade neste dia."
- **Arquivos:**
  - `static/js/Geral/calendario-eventos.js` (`criarMarcadorEstudo`);
  - `templates/Geral/calendario-eventos.html` (bloco `planoDados`);
  - `ViabilidadeService.java` (`planejar`).

#### B2. Modal "Plano de estudos" do dia
- **Login:** aluno.
- **Passos:**
  1. Clique no botão "Estudar 8h" de cada dia abaixo.
- **Esperado** (conteúdo · evento · tempo):

| Dia | Itens no modal |
|---|---|
| D0 | QA 02 · QA Prova 1 · **2h**<br>QA 04 · QA Trabalho 2 · **1h · sugestão**<br>QA 06 · QA Prova final · **5h · sugestão** |
| D+1 | QA 03 · QA Prova 1 · **1h**<br>QA 05 · QA Seminário 3 · **1h**<br>QA 04 · **1h · sugestão**<br>QA 06 · **5h · sugestão** |
| D+2 | QA 05 · QA Seminário 3 · **2h · sugestão** (1h cadastrada + 1h sugerida, agrupadas)<br>QA 04 · **1h · sugestão**<br>QA 06 · **5h · sugestão** |
| D+4 | QA 05 · **2h**<br>QA 04 · **1h · sugestão**<br>QA 06 · **5h · sugestão** |
| D+6 e D+7 | QA 02 · **QA Prova final** · **1h**<br>QA 06 · **7h · sugestão** |
| D+8 | QA 06 · **8h · sugestão** |

  - O rodapé do modal tem o botão **"Ir para [QA] Planejamento de Estudos"**, que leva a `/turmas/ID_A`.
  - O modal abre **abaixo do cabeçalho**. O cabeçalho continua visível e sem desfoque.
  - O fundo atrás do modal fica escurecido/desfocado.
- **Arquivos:**
  - `calendario-eventos.js` (`abrirPlanoDoDia`, `agruparPlanoPorConteudo`);
  - `templates/Geral/calendario-eventos.html` (`#planoEstudoModal`);
  - `static/css/Geral/calendario-eventos.css`;
  - `static/css/campus-theme.css`.

#### B3. 🔁 Concluir o Projeto final muda o plano
- **Login:** aluno.
- **Passos:**
  1. Em `/turmas/ID_A`, no card **QA 06 - Projeto final**, clique em **Marcar como concluído**.
  2. Volte para `/eventos`.
- **Esperado:**
  - D0, D+1, D+2 e D+4: **"Estudar 3h"**, ainda no estilo sugestão por causa de Laços/Recursão.
  - D+6 e D+7: **"Estudar 1h"**, no estilo **normal**: sem sugestão, só disponibilidade cadastrada.
  - D+8: **sem marcador**.
  - Em `/score`: QA 06 some de Prioridades, e a Prova final passa a **Replanejar**.
- **Arquivos:**
  - `java/controller/Geral/turmasController.java` (`/concluir`);
  - `ViabilidadeService.java`;
  - `calendario-eventos.js`.

#### B4. 🔁 Botão "Refazer" no card do evento
- **Login:** aluno.
- **Passos:**
  1. Em `/eventos`, clique no dia **D+3** e localize o card **QA Prova 1**.
  2. Clique em **Mostrar conteúdos**.
  3. Ao lado de "QA 01 - Introdução ✔ Concluído", clique em **Refazer**.
  4. No modal, clique em **Refazer conteúdo**.
- **Esperado:**
  - O modal "Refazer conteúdo" cita o nome do conteúdo e diz que ele volta ao plano e ao cálculo de risco.
  - Depois de confirmar, aparece a mensagem "**O conteúdo voltou a ficar pendente.**". QA 01 perde o ✔.
  - Em `/score/turma/ID_A`, a Prova 1 continua **Possível**: "Faltam 4h de estudo e você tem 5h disponíveis até D+3." Barra: 0/3.
  - **Negativo:** no card do **QA Quiz passado** (D-3), QA 08 aparece "✔ Concluído" **sem** botão Refazer, porque o evento já passou.
- **Arquivos:**
  - `templates/Geral/calendario-eventos.html` (`#confirmarRefazerModal`);
  - `calendario-eventos.js`;
  - `turmasController.java` (`concluirConteudo`, `origem=eventos`).

### Bloco C: aluno, página da turma (`/turmas/ID_A`)

#### C1. Conteúdos com prazo encerrado
- **Login:** aluno.
- **Passos:**
  1. Acesse `/turmas/ID_A`.
  2. Role pela lista de conteúdos.
- **Esperado:**
  - **QA 07** e **QA 12**: selo "**Não concluído · prazo encerrado em D-3**" e **sem** o botão "Marcar como concluído".
  - **QA 09 - Lista reaberta**: **tem** o botão "Marcar como concluído". O evento passou, mas o professor reabriu.
  - **QA 08**: aparece como concluído, com o botão **Refazer tarefa**.
  - Na turma B (`/turmas/ID_B`), QA B1 e QA B2: "Não concluído · prazo encerrado em D-2".
- **Arquivos:**
  - `templates/Geral/ambienteTurma.html`;
  - `java/service/ConteudoService.java` (`listarPrazosEncerrados`);
  - `java/repository/ConteudoRepository.java` (`listarPrazosEncerrados`).

#### C2. 🔁 Desfazer conclusão de conteúdo vencido
- **Login:** aluno.
- **Passos:**
  1. Em `/turmas/ID_A`, no QA 08, clique em **Refazer tarefa**.
- **Esperado:**
  - A conclusão é desfeita (desmarcar é permitido mesmo após o prazo).
  - Em seguida, QA 08 mostra "Não concluído · prazo encerrado em D-3" e **não** oferece mais "Marcar como concluído".
- **Arquivos:**
  - `ConteudoService.java` (`alterarConclusao`);
  - `ambienteTurma.html`.

#### C3. 🔁 Concluir conteúdo reaberto
- **Login:** aluno.
- **Passos:**
  1. Em `/turmas/ID_A`, clique em **Marcar como concluído** no QA 09.
- **Esperado:** o conteúdo fica concluído normalmente.
- **Arquivos:** `ConteudoService.java` (`alterarConclusao`).

### Bloco D: professor, Acompanhamento (`/acompanhamento`)

#### D1. Visão da turma
- **Login:** professor.
- **Passos:**
  1. Menu **Acompanhamento**.
  2. Clique em **[QA] Planejamento de Estudos** (`/acompanhamento/turma/ID_A`).
- **Esperado:**
  - Topo: "**3 alunos**".
  - **Conteúdos críticos** (recolhido; badge vermelho **1**). Ao abrir: "QA 06 - Projeto final — **3 alunos sem tempo**".
    - Só entra Impossível. Replanejar e vencidos **não** entram.
  - **Alunos com conteúdos não concluídos** (recolhido; badge **2**). Ao abrir:
    - "Bruno Ferreira (Fictício) — **2 conteúdos não concluídos**" (QA 07 e QA 12);
    - "eddie alencar costa — **2 conteúdos não concluídos**".
    - Ana **não** aparece: ela concluiu tudo que venceu.
    - QA 09 (reaberto) não conta para ninguém.
  - **Próximos eventos**: aqui a ordem é por **horário**: Prova 1, **Trabalho 2, Seminário 3**, Prova final.
  - Contadores verde / amarelo / vermelho (e ✔ quando houver):

| Evento | Contadores do evento | Por conteúdo |
|---|---|---|
| QA Prova 1 | 1 / 2 / 0 | QA 01: 0/1/0 + ✔2<br>QA 02: 1/1/0 + ✔1<br>QA 03: 1/2/0 |
| QA Trabalho 2 | 0 / 3 / 0 | QA 04: 0/3/0 |
| QA Seminário 3 | 0 / 3 / 0 | QA 05: 0/3/0 |
| QA Prova final | 0 / 0 / 3 | QA 02: 0/2/0 + ✔1<br>QA 06: 0/0/3 |

  - **Eventos passados** (recolhido; badge **2**):
    - "QA Lista reaberta · D-1 10:00";
    - "QA Quiz passado · D-3 10:00".
- **Arquivos:**
  - `java/controller/Usuarios/Professor/AcompanhamentoController.java` (`exibirAcompanhamentoTurma`);
  - `templates/Usuarios/Professor/acompanhamentoTurma.html`;
  - `ViabilidadeService.java`.

#### D2. Tabela de alunos de um evento futuro
- **Login:** professor.
- **Passos:**
  1. Em D1, expanda **QA Prova 1**.
  2. Clique em **Ver alunos**.
  3. Depois faça o mesmo com **QA Prova final**.
- **Esperado, Prova 1** (ordem: mais grave primeiro, depois por nome):

| Aluno | QA 01 | QA 02 | QA 03 | Conclusão | Concluído (7 dias) | Situação |
|---|---|---|---|---|---|---|
| Ana Ferreira | ✔ | ✔ | amarelo | 2/3 | 5h15 | Replanejar |
| Bruno Ferreira | amarelo | amarelo | amarelo | 0/3 | 30min | Replanejar |
| eddie alencar costa | ✔ | verde | verde | 1/3 | 11h | Possível |

- **Esperado, Prova final:** todos com Situação **Impossível**, na ordem Ana, Bruno, eddie.
  - QA 06 vermelho para os 3.
  - QA 02: ✔ para Ana e amarelo para os outros.
  - Conclusão: Ana 1/2, Bruno 0/2, eddie 0/2.
- **Arquivos:**
  - `AcompanhamentoController.java` (`exibirAcompanhamentoEvento`);
  - `templates/Usuarios/Professor/acompanhamentoEvento.html`.

#### D3. Tabela de um evento passado
- **Login:** professor.
- **Passos:**
  1. Em D1, abra **Eventos passados**.
  2. Clique em **Ver alunos** do **QA Quiz passado**.
- **Esperado:**
  - Selo "**Evento encerrado**".
  - Colunas QA 07, QA 08 e QA 12. Cada célula mostra só ✔ ou ponto "Não concluído", nunca verde/amarelo/vermelho.

| Aluno | QA 07 | QA 08 | QA 12 | Conclusão | Situação |
|---|---|---|---|---|---|
| Bruno Ferreira | Não concluído | ✔ | Não concluído | 1/3 | Não concluído |
| eddie alencar costa | Não concluído | ✔ | Não concluído | 1/3 | Não concluído |
| Ana Ferreira | ✔ | ✔ | ✔ | 3/3 | Concluído |

- **Observação (comportamento atual, não é bug):** a tabela do evento **QA Lista reaberta** mostra os 3 alunos como "Não concluído" em QA 09. A tabela de um evento passado só olha se o aluno concluiu, mesmo que o conteúdo tenha sido reaberto depois.
- **Arquivos:** `AcompanhamentoController.java` (`exibirAcompanhamentoEvento`, ramo `eventoPassado`).

### Bloco E: professor, prazos e Re-disponibilizar

#### E1. Quem pode re-disponibilizar
- **Login:** professor.
- **Passos:**
  1. Abra `/turmas/ID_A`.
  2. Abra `/turmas/ID_B`.
- **Esperado em ID_A**, onde Eddie é o **dono**:
  - QA 07, QA 08 e QA 12 mostram "**Prazo encerrado em D-3**" **e** o botão **Re-disponibilizar**.
  - QA 12 é de outra autora, mas o dono da turma pode re-disponibilizar.
  - QA 09 **não** tem selo, porque já foi reaberto.
- **Esperado em ID_B**, onde Eddie é **membro, não dono**:
  - **QA B1** (autora Ana Almeida): selo "Prazo encerrado em D-2", **sem** botão.
  - **QA B2** (autor Eddie): selo **e** botão Re-disponibilizar.
- **Arquivos:**
  - `ambienteTurma.html`;
  - `turmasController.java` (`isTurmaAdmin`, `idProfessorLogado`);
  - `ConteudoRepository.java` (`reabrir`).

#### E2. 🔁 Re-disponibilizar e ver o efeito no aluno
- **Login:** professor, depois aluno.
- **Passos:**
  1. Professor: em `/turmas/ID_A`, clique em **Re-disponibilizar** no QA 07.
  2. Aluno: recarregue `/turmas/ID_A`.
  3. Professor: abra `/acompanhamento/turma/ID_A`.
- **Esperado:**
  - Mensagem "**Conteúdo disponível novamente para os alunos concluírem.**". O selo "Prazo encerrado" do QA 07 some.
  - Aluno: QA 07 sem o selo vermelho e **com** "Marcar como concluído".
  - Acompanhamento: Bruno e eddie passam de 2 para **1 conteúdo não concluído**.
- **Arquivos:**
  - `turmasController.java` (`reDisponibilizarConteudo`);
  - `ConteudoService.java` (`reabrir`).

#### E3. 🔁 Meus conteúdos
- **Login:** professor.
- **Passos:**
  1. Menu do perfil → **Meus conteúdos** (`/meus-conteudos`).
- **Esperado:**
  - QA 07 e QA 08: "Prazo encerrado em D-3" + botão Re-disponibilizar.
  - QA B2: "Prazo encerrado em D-2" + botão.
  - QA 12 e QA B1 **não** aparecem, porque não são dele.
  - Clicar em Re-disponibilizar no QA B2 mostra "Conteúdo disponível novamente para os alunos concluírem." e o selo some.
- **Arquivos:**
  - `java/controller/Usuarios/Professor/MeusConteudosController.java`;
  - `templates/Usuarios/Professor/meusConteudos.html`.

### Bloco F: professor, cadastro de evento (`/eventos/cadastrar`)

#### F1. Horário inválido
- **Login:** professor.
- **Passos:**
  1. Acesse `/eventos/cadastrar`.
  2. Preencha: nome "QA teste horário", turma **[QA] Planejamento de Estudos**, data **D+6**, início **15:00**, fim **14:00**.
  3. Clique em **Salvar**.
- **Esperado:**
  - O navegador **não envia** o formulário.
  - Aparece no campo de fim: "**O horário de término deve ser depois do início, no mesmo dia.**".
  - Nenhum evento é criado.
- **Arquivos:**
  - `static/js/Geral/cadastrar-eventos.js`;
  - `java/service/EventoService.java` (`validarHorario`, validação do lado do servidor).

#### F2. Prévia do evento na agenda do dia
- **Login:** professor.
- **Passos:**
  1. Preencha uma data com horário válido, por exemplo D+5, 14:00–15:00.
- **Esperado:**
  - A agenda do dia, à direita, mostra os eventos existentes do dia (QA Trabalho 2 e QA Seminário 3).
  - Mostra também a **prévia** do novo evento no horário escolhido, claramente diferenciada como prévia.
  - Mudar a hora move a prévia.
- **Arquivos:**
  - `cadastrar-eventos.js`;
  - `templates/Geral/cadastrar-eventos.html`;
  - `static/css/Geral/cadastrar-eventos.css`;
  - `static/css/Geral/agenda-dia.css`.

#### F3. 🔁 Aviso de conteúdo vencido ao criar evento
- **Login:** professor.
- **Passos:**
  1. Nome "QA Reposição", tipo Prova, turma **[QA] Planejamento de Estudos**, data **D+6**, 10:00–11:00.
  2. Abra a lista de conteúdos relacionados.
  3. Selecione **QA 07**.
  4. Clique em **Salvar**.
  5. No modal, clique primeiro em **Cancelar**.
  6. Depois salve de novo e clique em **Criar evento mesmo assim**.
- **Esperado:**
  - Na lista de conteúdos, QA 07, QA 08 e QA 12 aparecem com "**⏱ Vencido em D-3**".
  - Ao salvar abre o modal "**Conteúdos com prazo encerrado**", listando QA 07 com a tag "Vencido em D-3".
  - **Cancelar**: fecha o modal e nada é criado.
  - **Criar evento mesmo assim**: o evento é criado. Depois disso:
    - QA 07 volta a poder ser concluído pelo aluno, porque agora tem um evento futuro.
    - O selo "Prazo encerrado" do QA 07 some para o professor.
  - Efeito colateral esperado: D+6 vira dia de evento e deixa de contar como dia de estudo. Por isso os números do Score mudam. **Rode o script de massa de novo.**
- **Arquivos:**
  - `cadastrar-eventos.js` (bloco `avisoConteudosVencidos`);
  - `cadastrar-eventos.html`;
  - `EventosController.java` (`/eventos/conteudos`, `/cadastrar`).

### Bloco G: turmas recentes (`/turmas` e `/acompanhamento`)

#### G1. 🔁 Turma alterada vai para "Recentes"
- **Login:** professor.
- **Passos:**
  1. Em `/turmas/ID_B`, clique em **Re-disponibilizar** no QA B2.
  2. Abra `/turmas`.
  3. Abra `/acompanhamento`.
  4. Volte a `/turmas/ID_A` e re-disponibilize o QA 12.
  5. Abra `/turmas` de novo.
- **Esperado:**
  - `/turmas` mostra a seção **Recentes**, com até 5 turmas, ícone de relógio, e "[QA] Turma da Prof. Ana Almeida" **em primeiro**.
  - Abaixo vem **Todas as turmas**, em **ordem alfabética**, repetindo também as recentes.
  - `/acompanhamento` mostra a mesma lógica. Os links levam para `/acompanhamento/turma/{id}`.
  - Depois do passo 4: "[QA] Planejamento de Estudos" passa a ser a **primeira** de Recentes.
  - Só **abrir** uma turma não muda a ordem. Só alterações contam: concluir conteúdo, re-disponibilizar, criar/editar conteúdo etc.
  - O mesmo vale para o aluno: concluir um conteúdo na turma B coloca a turma B no topo dos Recentes dele.
- **Arquivos:**
  - `java/repository/TurmaRepository.java` (`registrarAlteracao`, `listarTurmasRecentes`, `recentesDentre`, `ordenarPorNome`);
  - `turmasController.java`;
  - `AcompanhamentoController.java`;
  - `templates/Geral/turmas.html`;
  - `templates/Usuarios/Professor/acompanhamento.html`.

### Bloco H: segurança e telas de erro

#### H1. Página inexistente
- **Login:** qualquer um.
- **Passos:**
  1. Acesse `http://localhost:8080/pagina-que-nao-existe`.
- **Esperado:**
  - Cartão com o logo, "**ERRO 404**", título "**Página não encontrada**".
  - Botões "Voltar à página anterior" e "Voltar para o início".
  - **Nenhum** texto técnico na página: stack trace, nome de classe Java, "Whitelabel" etc.
- **Arquivos:**
  - `templates/error.html`;
  - `CampusLink/src/main/resources/application.properties` (`server.error.*` e `spring.web.error.*`).

#### H2. Ação por caminho errado
- **Login:** aluno.
- **Passos:**
  1. Digite na barra de endereço `http://localhost:8080/turmas/ID_A/conteudos/1/concluir` e dê Enter. Isso é um GET numa rota que só aceita POST.
- **Esperado:** "**ERRO 405**", título "**Ação não permitida**", sem detalhes técnicos.
- **Arquivos:** `templates/error.html`.

#### H3. Acesso de papel errado
- **Passos:**
  1. Logado como **professor**, acesse `/score`.
  2. Logado como **aluno**, acesse `/score/turma/52`. O aluno foi retirado dessa turma pela massa.
- **Esperado:**
  - Passo 1: o professor é redirecionado para `/turmas`.
  - Passo 2: o aluno volta para `/score` com a mensagem "**Você não participa desta turma.**".
- **Arquivos:** `ScoreController.java` (`verificarAcesso`, `exibirScoreTurma`).

### Bloco I: interface geral

#### I1. Cabeçalho fixo e modais
- **Passos:**
  1. Em qualquer página longa (por exemplo `/turmas/ID_A`), role até o fim.
  2. Abra qualquer modal: plano do dia, Refazer, criar turma, aviso de vencidos.
  3. Teste também com a janela estreita (celular).
- **Esperado:**
  - O cabeçalho **nunca sai da tela**.
  - Modais e o fundo escurecido começam **abaixo** do cabeçalho. O cabeçalho não fica desfocado nem coberto.
  - O modal fica clicável: não pode ficar "atrás" do fundo escuro.
- **Arquivos:**
  - `templates/fragments/header.html` (script que mede a altura em `--campus-header-altura`);
  - `static/css/campus-theme.css` (`--campus-overlay`, `--campus-overlay-blur`).

---

## 5. Ao terminar

1. Abra o **SQL Editor** do Supabase.
2. Rode `qa_v2_2_restaurar_estado_original.sql`.
3. Para conferir:
   - as turmas **[QA]** somem de `/turmas`;
   - o aluno volta para a turma 52.

> O script de restauração devolve os dados do aluno Eddie (disponibilidade e conclusões) **como estavam em 01/11/2026**.
> Se o Eddie usou a conta de aluno de verdade depois dessa data, avise antes de rodar.

## 6. Como reportar um problema

Para cada falha, envie:

- o **ID do cenário** (ex.: A2);
- o **passo** em que falhou;
- o que era **esperado** × o que **apareceu**;
- um **print**;
- a **data real** da tabela do passo 1.2.

Se o problema for numérico (horas ou selo errado), cite também se algum cenário 🔁 foi executado antes **sem** rodar o script de novo.
