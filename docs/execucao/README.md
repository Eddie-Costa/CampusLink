# Instruções e evidências de execução do CampusLink

Este documento mostra como executar o CampusLink, como carregar os dados de demonstração e como verificar cada funcionalidade e integração. No fim estão as evidências (capturas de tela, resultado dos testes automatizados e respostas reais das integrações).

Nenhuma senha, token, chave de API ou dado pessoal real aparece aqui. Todas as pessoas citadas são fictícias, e os emails usam os domínios `exemplo.com` ou um endereço de teste. Nas capturas das telas de administração, os dados das contas reais de administrador foram trocados por "oculto@exemplo.com".

## 1. Pré-requisitos

| Item | Versão ou observação |
|---|---|
| Java (JDK) | 25 |
| Maven | já vem no projeto (`mvnw` / `mvnw.cmd`) |
| PostgreSQL | banco no Supabase (ou qualquer PostgreSQL com o mesmo esquema) |
| Supabase Storage | bucket para os arquivos enviados nos conteúdos |
| Brevo (SMTP) | envio dos emails de verificação em duas etapas e de recuperação de senha |
| YouTube Data API v3 | busca de vídeos ao cadastrar conteúdo |
| Google Books API | busca de livros ao cadastrar conteúdo |
| Docker (opcional) | para rodar pela imagem do `Dockerfile` |

## 2. Configuração

A aplicação lê as configurações de variáveis de ambiente ou de um arquivo `.env` na pasta `CampusLink/`. O arquivo `.env` **não** está no repositório: cada pessoa cria o seu com as próprias credenciais.

| Variável | Para que serve | Exemplo fictício |
|---|---|---|
| `DB_URL` | endereço JDBC do PostgreSQL, com usuário e senha | `jdbc:postgresql://host:5432/postgres?user=usuario&password=***` |
| `DB_POOL_SIZE` | conexões simultâneas com o banco (padrão 5) | `5` |
| `BREVO_HOST`, `BREVO_PORT` | servidor SMTP do Brevo | `smtp-relay.brevo.com`, `587` |
| `BREVO_USERNAME`, `BREVO_PASSWORD` | credenciais SMTP do Brevo | `***` |
| `MAIL_LOCALHOST` | nome do servidor na conversa SMTP | `campuslink.local` |
| `SUPABASE_URL` | endereço do projeto Supabase | `https://xxxx.supabase.co` |
| `SUPABASE_SECRET_KEY` | chave de serviço do Supabase Storage | `***` |
| `SUPABASE_STORAGE_BUCKET` | nome do bucket dos arquivos | `arquivos` |
| `YOUTUBE_API_KEY` | chave da YouTube Data API v3 | `***` |
| `GOOGLE_BOOKS_API_KEY` | chave da Google Books API | `***` |
| `PORT` | porta HTTP (padrão 8080) | `8080` |
| `VIABILIDADE_HORIZONTE_DIAS` | quantos dias à frente entram no cálculo de risco (padrão 90) | `90` |

A verificação em duas etapas é controlada por `campuslink.twofa.enabled` no `application.properties` (`true` exige o código por email).

## 3. Como executar

**Pelo Maven** (na pasta `CampusLink/`):

```bash
./mvnw spring-boot:run          # Linux e macOS
mvnw.cmd spring-boot:run        # Windows
```

**Pelo Docker** (na pasta `CampusLink/`):

```bash
docker build -t campuslink .
docker run --env-file .env -p 8080:8080 campuslink
```

Depois, abra `http://localhost:8080`.

**Testes automatizados** (na pasta `CampusLink/`):

```bash
./mvnw test
# com relatório de cobertura em target/site/jacoco/index.html
./mvnw org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

## 4. Dados de demonstração

Os scripts ficam em [`banco-de-dados/`](../../banco-de-dados) e são executados no SQL Editor do Supabase (ou em qualquer cliente PostgreSQL):

1. **`01_limpar_banco.sql`** apaga todos os dados e mantém só o essencial: as versões dos Termos de Uso, os administradores master e os sites já liberados. Tudo roda em uma transação: se algo falhar, nada é apagado.
2. **`02_massa_apresentacao.sql`** cria a massa de demonstração. Antes de rodar, preencha no início do script:
   - `v_email_base`: o início de uma caixa de email que você controla (as contas viram `base+aluna@…`, `base+professora@…` e `base+admin@…`, e os códigos de verificação chegam nessa caixa);
   - `v_senha_demo`: a senha das três contas de demonstração (com maiúscula, minúscula, número e símbolo).

As datas da massa são calculadas a partir do dia em que o script roda. Por isso ele deve ser executado no próprio dia da demonstração.

| Conta de demonstração | Perfil | Para que serve |
|---|---|---|
| Ana Souza (`base+aluna@…`) | Aluna | Mostra todos os níveis de risco, o plano de estudos, o score, denúncia e chamados |
| Renata Oliveira (`base+professora@…`) | Professora | Mostra turmas, conteúdos, prazos, acompanhamento e pedidos de site |
| Patrícia Alves (`base+admin@…`) | Administradora master | Mostra moderação, sites, chamados, usuários, logs e painéis |

Os outros 30 usuários (`@exemplo.com`) só preenchem as tabelas e não são usados para entrar.

## 5. Roteiro de verificação

Cada passo indica o resultado esperado e, quando houver, a evidência correspondente (seção 7).

### 5.1 Acesso e segurança

| Passo | Resultado esperado | Evidência |
|---|---|---|
| Abrir `/login` e entrar com uma conta de demonstração | Pede o código de 6 dígitos enviado por email; depois abre o início | [01](evidencias/01_login.png) |
| Errar o código 5 vezes | O código é descartado e é preciso pedir outro | testes de segurança |
| Usar "Esqueci a senha" com um email inexistente | Mostra a mesma tela de código, sem enviar email | testes de segurança |
| Entrar com a Ana na primeira vez | Aparece o aviso de termos pendentes; após aceitar a versão atual, as funções são liberadas | — |
| Clicar em Sair | A sessão termina (o logout só aceita POST com CSRF) | — |

### 5.2 Aluna (Ana Souza)

| Passo | Resultado esperado | Evidência |
|---|---|---|
| Abrir **Meu Dashboard** | Indicadores de progresso e próximos eventos | [03](evidencias/03_aluno_dashboard.png) |
| Abrir **Turmas** e entrar em **Banco de Dados** | Cartões com número de conteúdos e eventos; conteúdos da turma | [04](evidencias/04_aluno_turmas.png), [05](evidencias/05_aluno_turma_banco_de_dados.png) |
| Abrir **Score** | Cada evento com seu risco: Prova de Estatística (amanhã) **impossível**, Entrega de requisitos (4 dias) **possível**, P1 de Banco de Dados (7 dias) **replanejar**, Apresentação web (12 dias) **concluída**, Lista 1 (vencida) **atrasada**, Índices **sem evento** | [06](evidencias/06_aluno_score.png), [07](evidencias/07_aluno_score_turma.png) |
| Abrir **Plano de Estudos** | Calendário com eventos, horas livres e sessões de estudo sugeridas | [08](evidencias/08_aluno_plano_de_estudos.png) |
| Cadastrar horas livres em um dia | O plano e o risco são recalculados na hora | — |
| Concluir um conteúdo | O progresso e o score mudam | — |
| Denunciar um conteúdo | O conteúdo fica suspenso até a análise do admin | — |
| Abrir **Ajuda** e **Meus chamados** | Chamados aberto, respondido (com conversa) e concluído | [09](evidencias/09_aluno_meus_chamados.png) |

Nas capturas da seção do aluno foi usada uma conta de apoio da mesma turma, para preservar o primeiro acesso da Ana com o aceite dos termos.

### 5.3 Professora (Renata Oliveira)

| Passo | Resultado esperado | Evidência |
|---|---|---|
| Abrir **Meu Dashboard** | Indicadores das turmas e dos conteúdos | [10](evidencias/10_professora_dashboard.png) |
| Entrar em **Banco de Dados** | Conteúdos com prazo encerrado e botão **Re-disponibilizar** | [11](evidencias/11_professora_turma.png) |
| Adicionar um conteúdo pelo YouTube ou pelo Google Livros | A busca traz resultados reais e preenche o conteúdo | [25](evidencias/25_integracoes_busca.json) |
| Abrir **Meus Conteúdos** | Lista dos conteúdos com situação (ativo, suspenso, removido) e **Escolher turmas**; "Boas práticas com Git" aparece em duas turmas | [12](evidencias/12_professora_meus_conteudos.png) |
| Solicitar análise do conteúdo suspenso | O pedido vai para o administrador | — |
| Abrir **Acompanhamento** de Banco de Dados | Quantos alunos estão em cada situação por evento e conteúdos críticos | [13](evidencias/13_professora_acompanhamento.png) |
| Abrir **Eventos** | Calendário das turmas da professora | [14](evidencias/14_professora_eventos.png) |
| Pedir liberação de um site | O pedido aparece como "Aguardando análise" | — |

### 5.4 Administradora (Patrícia Alves)

| Passo | Resultado esperado | Evidência |
|---|---|---|
| Abrir **Painel Administrativo** | Atalhos com pendências (chamados, denúncias, sites) | [15](evidencias/15_admin_painel.png) |
| Abrir **Meu Dashboard** | Indicadores de contas, turmas e moderação | [16](evidencias/16_admin_dashboard.png) |
| Abrir **Usuários** | Lista com perfis e situação; reativar a conta desativada (Sofia Ribeiro) | [17](evidencias/17_admin_usuarios.png) |
| Abrir **Conteúdos denunciados** | "Lista extra de modelagem" com denúncias; liberar ou remover com comentário | [18](evidencias/18_admin_denuncias.png) |
| Abrir **Sites liberados** | Pedido do w3schools.com para liberar ou recusar | [19](evidencias/19_admin_sites.png) |
| Abrir **Chamados** | Chamados abertos, respondidos e concluídos; responder e concluir | [20](evidencias/20_admin_chamados.png) |
| Abrir **Logs de Sessões** | Sessões encerradas com pesquisa, filtros e detalhe; dados sensíveis ocultados | [21](evidencias/21_admin_logs_sessoes.png) |

### 5.5 Acessibilidade e interface

| Passo | Resultado esperado | Evidência |
|---|---|---|
| Ativar o modo escuro no cabeçalho | Todas as telas mudam para o tema escuro | [22](evidencias/22_modo_escuro_plano_de_estudos.png) |
| Abrir pelo celular | Menu recolhível e telas sem rolagem lateral | [23](evidencias/23_celular_turma.png) |

## 6. Integrações

| Integração | Onde é usada | Como verificar | Evidência |
|---|---|---|---|
| PostgreSQL (Supabase) | Todos os dados do sistema | Qualquer tela com dados da massa | capturas 03 a 23 |
| Supabase Storage | Arquivos anexados aos conteúdos | Adicionar um conteúdo do tipo arquivo e abrir o download | testes `SupabaseStorageServiceTests` |
| Brevo (SMTP) | Código de verificação, recuperação de senha e avisos de chamado | Entrar com uma conta de demonstração e receber o código | testes `ChamadoEmailPdfTests` e `TwoFactorServiceTests` |
| YouTube Data API v3 | Busca de vídeos ao cadastrar conteúdo | `GET /youtube/buscar?q=...` logado como professor | [25](evidencias/25_integracoes_busca.json) |
| Google Books API | Busca de livros ao cadastrar conteúdo | `GET /googlebooks/buscar?q=...` logado como professor | [25](evidencias/25_integracoes_busca.json) |

## 7. Evidências

Todas foram geradas em 15/11/2026, com a massa de demonstração carregada e o sistema rodando localmente ligado ao banco do Supabase.

| Arquivo | Conteúdo |
|---|---|
| `01_login.png` | Tela de login |
| `03` a `09` | Telas do aluno: dashboard, turmas, turma, score, score da turma, plano de estudos e chamados |
| `10` a `14` | Telas da professora: dashboard, turma, meus conteúdos, acompanhamento e eventos |
| `15` a `21` | Telas da administradora: painel, dashboard, usuários, denúncias, sites, chamados e logs de sessões |
| `22_modo_escuro_plano_de_estudos.png` | Plano de estudos no modo escuro |
| `23_celular_turma.png` | Página da turma em tela de celular |
| [`24_testes_automatizados.txt`](evidencias/24_testes_automatizados.txt) | 425 testes executados, 0 falhas, 0 erros; cobertura de 76,6% das instruções e 74,4% das linhas (JaCoCo) |
| [`25_integracoes_busca.json`](evidencias/25_integracoes_busca.json) | Respostas reais das buscas no YouTube e no Google Livros (só dados públicos: título, canal ou autor, duração e miniatura) |

Durante o desenvolvimento também foram executadas baterias de carga, de segurança (IDOR, CSRF, XSS, bloqueio de login, sessão, 2FA, logout e recuperação de senha) e de interface (62 telas em desktop e celular, sem erros de JavaScript e sem falhas de contraste).
