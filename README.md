# 🎓 CampusLink

### Plataforma web para organização e planejamento de estudos entre professores e alunos

O **CampusLink** é uma plataforma web desenvolvida como Trabalho de Conclusão de Curso (Engenharia de Software, UMC). Ela reúne turmas, conteúdos, eventos acadêmicos, calendário e planejamento de estudos em um só lugar. A partir da prioridade e da duração dos conteúdos definidas pelo professor e das horas livres informadas pelo aluno, o sistema sugere um plano de estudos e mostra um indicador de risco antes de cada evento.

> O CampusLink avalia a **viabilidade do planejamento**, não o desempenho do aluno. Não atribui notas.

## ❗ Problema

Estudar em um curso superior significa lidar, ao mesmo tempo, com várias disciplinas, cada uma com seus materiais, provas e entregas. Na prática, o que o aluno precisa saber fica espalhado: um aviso chega por mensagem, um arquivo por e-mail, uma data aparece em outro sistema. Sem uma visão única, é difícil decidir por onde começar e quanto tempo reservar para cada assunto antes de uma avaliação.

O professor sente o outro lado dessa desorganização. Ele publica o material, mas não enxerga com clareza quem está preparado para o prazo e quem está ficando para trás. A coordenação, por sua vez, administra contas, conteúdos e dúvidas sem um lugar central para isso.

## 💡 Solução

O CampusLink coloca tudo no mesmo ambiente. O professor organiza a turma, publica os materiais, informa quanto tempo cada um leva e marca as datas importantes. O aluno diz em quais dias tem horas livres. O sistema cruza essas informações e monta uma sugestão de estudo, indicando se o tempo disponível é suficiente para chegar preparado a cada evento. Ao concluir os conteúdos, o aluno atualiza o plano, e o professor acompanha o andamento da turma. O administrador cuida de usuários, moderação e atendimento em um painel próprio.

## 🎯 Objetivo

Oferecer uma plataforma web que ajude o aluno a organizar o próprio estudo a partir do que o professor publica, e que dê ao professor e ao administrador as informações de que precisam para acompanhar as turmas e a plataforma. Para isso, o sistema deve:

- centralizar conteúdos e eventos de cada turma;
- registrar o tempo que o aluno tem para estudar;
- sugerir um plano de estudos e indicar o risco de cada evento;
- separar as telas e as permissões de aluno, professor e administrador;
- proteger os dados pessoais dos usuários.

## 📌 Funcionalidades

- 🔐 Cadastro de alunos e professores com confirmação por e-mail, login com código de verificação em duas etapas e recuperação de senha.
- 👥 Turmas com participantes adicionados por e-mail.
- 📚 Conteúdos em quatro formatos: link, vídeo do YouTube, livro do Google Books e arquivo (até 50 MB; PDF, PNG, JPG, JPEG, DOC, DOCX, PPT, PPTX), com busca e filtro por tipo.
- 🌐 Links aceitos somente de sites liberados pelo administrador (professor solicita, administrador aprova ou recusa).
- 📅 Eventos acadêmicos (prova, trabalho, apresentação, outro prazo) com prioridade e conteúdos associados.
- ⏱️ Disponibilidade de estudo por data (0 a 24 h), plano de estudos e Score com risco por evento.
- 📈 Acompanhamento do professor por turma e evento, com conteúdos críticos.
- 🚩 Denúncia de conteúdos pelos alunos, suspensão automática e revisão pelo administrador.
- 📊 Dashboards separados para aluno, professor e administrador.
- 🎧 Canal de ajuda (chamados) com histórico em PDF na conclusão.
- 🛡️ LGPD: termos de uso por versão com aceite e revogação, exportação dos dados por e-mail, exclusão de conta com anonimização.
- 🌙 Modo escuro, opção de reduzir movimento e layout adaptado a celular.

## 👥 Perfis de usuário

| Perfil | O que faz |
|---|---|
| 🎓 Aluno | Consulta conteúdos, informa disponibilidade, acompanha plano de estudos e risco, conclui conteúdos e denuncia conteúdos inadequados |
| 👨‍🏫 Professor | Cria turmas, publica conteúdos, cadastra eventos e acompanha os alunos |
| 🔑 Administrador | Gerencia usuários, turmas, conteúdos, eventos, sites liberados, termos de uso, chamados e logs de sessões, e modera conteúdos denunciados |

## ⚠️ Indicador de risco

| Situação no sistema | Cor | Significado |
|---|---|---|
| Possível | 🟢 Verde | As horas informadas bastam para concluir os conteúdos até o evento |
| Replanejar | 🟡 Amarelo | As horas informadas não bastam, mas a carga caberia em até 8 h de estudo por dia |
| Impossível | 🔴 Vermelho | Mesmo com 8 h por dia, o tempo até o evento é insuficiente |

Também aparecem as situações *Concluído*, *Não concluído* (prazo encerrado) e *Sem evento próximo*. Conteúdos sem duração informada contam 60 minutos. O cálculo é refeito a cada consulta, sem inteligência artificial.

## 🛠️ Tecnologias

| Camada | Tecnologias |
|---|---|
| Back-end | Java 25, Spring Boot 4.1.0, Spring Security (BCrypt, CSRF, perfis), Spring Data JPA, JdbcTemplate, Maven |
| Front-end | Thymeleaf, HTML, CSS, JavaScript, Bootstrap |
| Banco e arquivos | PostgreSQL e Storage no Supabase |
| Integrações | YouTube Data API v3, Google Books API, Brevo (SMTP) |
| Qualidade e docs | JUnit, Mockito, MockMvc, JaCoCo, Swagger UI (springdoc) |
| Ambiente | Docker, Git, GitHub |

Arquitetura em camadas (MVC, Service, Repository e DTO).

## 🚀 Como executar

Pré-requisitos: JDK 25, banco PostgreSQL (Supabase), bucket do Supabase Storage e as chaves das APIs. As configurações ficam em variáveis de ambiente ou em um arquivo `.env` na pasta `CampusLink/`, que não é versionado. A lista completa está em [`docs/execucao/README.md`](docs/execucao/README.md).

```bash
cd CampusLink
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
# ou, com Docker:
docker build -t campuslink .
docker run --env-file .env -p 8080:8080 campuslink
```

Acesse `http://localhost:8080`. Dados de demonstração: `banco-de-dados/01_limpar_banco.sql` e `02_massa_apresentacao.sql`.

## 🧪 Testes

```bash
cd CampusLink
./mvnw test
```

São 56 classes de teste (JUnit, Mockito e MockMvc), com 425 testes executados e cobertura de 76,6% das instruções (JaCoCo). Os testes cobrem autenticação, permissões, cálculo de viabilidade, denúncias, domínios, termos, chamados e integrações.

## 📖 Documentação

| Documento | Onde |
|---|---|
| Manual do usuário | Apêndice A da monografia (`Manual_CampusLink.docx`) |
| Guia de execução e evidências | `docs/execucao/README.md` |
| Integrações externas | `CampusLink/Documents/INTEGRACOES_EXTERNAS.md` |
| Monografia, especificação técnica, MER/DER e arquitetura | `CampusLink/Documents/` |
| Swagger UI (rotas de busca, perfil administrador) | `/swagger-ui.html` |

## 🔮 Trabalhos futuros

- Entrada em turmas por link ou QR Code com aprovação do professor
- Notificações acadêmicas sobre eventos próximos
- IA para sinalizar conteúdos potencialmente inadequados, com decisão final humana

## 👥 Equipe

Eddie Alencar Costa, Matheus Santhiago Fortonato de Souza e Riquelmy Christofer Saltes Acacio, Engenharia de Software, Universidade de Mogi das Cruzes (UMC).
