# 🎓 CAMPUSLINK

### Plataforma de Integração e Planejamento Acadêmico

O **CAMPUSLINK** é uma plataforma web desenvolvida como projeto de **TCC do curso de Engenharia de Software**, com o objetivo de facilitar a organização dos conteúdos acadêmicos e auxiliar os alunos no planejamento dos estudos.

A proposta é centralizar materiais disponibilizados pelos professores e relacioná-los com eventos acadêmicos, como provas, trabalhos e apresentações. A partir disso, o sistema considera a **prioridade dos conteúdos, o tempo necessário para estudá-los e a disponibilidade de cada aluno**, gerando um plano de estudos personalizado.

## 📌 Problema

Durante a graduação, os alunos precisam lidar com diversos conteúdos, atividades e prazos de diferentes disciplinas. Muitas vezes, existe dificuldade para organizar tudo isso de acordo com o tempo disponível.

O CAMPUSLINK busca solucionar esse problema transformando os conteúdos disponibilizados pelos professores em um **planejamento de estudos compatível com a disponibilidade do aluno**.

## 💡 Solução

A plataforma permite que:

* 👨‍🏫 Professores cadastrem conteúdos e eventos acadêmicos;
* 📚 Conteúdos sejam organizados por cursos, disciplinas e turmas;
* ⭐ Professores definam prioridade e duração estimada dos conteúdos;
* 📅 Alunos informem seus horários disponíveis para estudar;
* 📝 O sistema gere um plano de estudos individual;
* 📊 O progresso dos estudos seja acompanhado;
* 🔄 O planejamento seja recalculado quando houver alterações;
* ⚠️ O sistema identifique possíveis riscos de não conclusão dos conteúdos dentro do prazo;
* 📈 Professores acompanhem uma visão geral da situação da turma.

## 👥 Perfis de usuários

### 🎓 Aluno

Pode consultar os conteúdos, informar sua disponibilidade, acompanhar seu plano de estudos e registrar seu progresso.

### 👨‍🏫 Professor

Pode cadastrar conteúdos, eventos, prioridades e acompanhar indicadores relacionados às suas turmas.

### 🔑 Administrador

Responsável pelo gerenciamento de usuários, cursos, disciplinas, turmas e permissões.

## 🛠️ Tecnologias utilizadas

### Back-end

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* Maven

### Front-end

* Thymeleaf
* Bootstrap
* HTML
* CSS
* JavaScript

### Banco de dados e armazenamento

* PostgreSQL
* Supabase
* Supabase Storage

### APIs e integrações

* YouTube Data API
* Google Books API
* Google OAuth 2.0

### Testes e documentação

* JUnit
* Mockito
* Swagger / OpenAPI

### Ambiente

* Docker
* Git
* GitHub

A arquitetura do projeto foi organizada em camadas, separando apresentação, controle, serviços, regras de negócio e persistência, buscando facilitar a manutenção e os testes do sistema.

## ⚙️ Como funciona

O funcionamento principal do CAMPUSLINK segue o seguinte fluxo:

```text
Professor cadastra evento
        ↓
Associa conteúdos
        ↓
Define prioridade e duração
        ↓
Aluno informa disponibilidade
        ↓
Sistema calcula a capacidade de estudo
        ↓
Algoritmo gera o plano
        ↓
Aluno acompanha o planejamento
        ↓
Sistema realiza replanejamento quando necessário
        ↓
Professor acompanha os indicadores da turma
```

O planejamento considera o prazo do evento, os conteúdos associados, a duração estimada, a prioridade, a disponibilidade do aluno e o progresso registrado.

## ⚠️ Indicador de risco

O CAMPUSLINK possui um indicador para mostrar a **viabilidade do planejamento**, e não para avaliar o desempenho ou determinar a probabilidade de aprovação do aluno.

Os níveis utilizados são:

| Nível       | Significado                         |
| ----------- | ----------------------------------- |
| 🟢 Verde    | Planejamento viável                 |
| 🟡 Amarelo  | Atraso ou pouca margem de segurança |
| 🟠 Laranja  | Alta possibilidade de não conclusão |
| 🔴 Vermelho | Tempo disponível insuficiente       |

O indicador é baseado somente nos dados registrados no sistema e serve como apoio para o acompanhamento acadêmico.

## 🔐 Segurança

O sistema utiliza controle de acesso baseado em perfis, garantindo que cada usuário tenha acesso somente às funcionalidades e informações necessárias para sua função.

Também são considerados princípios relacionados à **LGPD**, principalmente em relação à coleta, utilização e acesso aos dados pessoais.

## 🧪 Testes

Para garantir o funcionamento das principais regras do sistema, serão utilizados testes automatizados com **JUnit e Mockito**.

Entre as funcionalidades testadas estão:

* Cálculo da capacidade de estudo;
* Distribuição das sessões;
* Priorização dos conteúdos;
* Cálculo do nível de risco;
* Replanejamento;
* Autenticação e autorização;
* Persistência dos dados;
* Integrações com serviços externos.

## 🚀 Evoluções futuras

Após a conclusão do MVP, algumas funcionalidades poderão ser adicionadas ao projeto, como:

* Integração com Google Calendar;
* Notificações em tempo real utilizando WebSocket;
* Elasticsearch para buscas;
* Redis para cache;
* Prometheus e Grafana para métricas;
* Recomendações automáticas de horários;
* Detecção de padrões históricos de atraso;
* Recomendações de materiais;
* Assistente acadêmico;
* Aplicativo mobile.

## 👨‍💻 Equipe

Projeto desenvolvido por estudantes do curso de **Engenharia de Software**:

* **Matheus Santhiago**
* **Eddie Alencar**
* **Riquelmy Christofer**

## 📚 Projeto acadêmico

Este projeto foi desenvolvido como parte do **Trabalho de Conclusão de Curso (TCC)** em Engenharia de Software, com foco na aplicação prática de conceitos de desenvolvimento web, arquitetura de software, banco de dados, segurança, testes e regras de negócio.

---

⭐ **CAMPUSLINK — conectando conteúdos, prazos e planejamento para tornar os estudos mais organizados.**
