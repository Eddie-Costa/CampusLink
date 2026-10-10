# CampusLink — documentação das integrações externas

## 1. Finalidade e escopo

O CampusLink consulta Google Books e YouTube para ajudar o professor a selecionar
materiais acadêmicos para as turmas. O Google Books fornece informações
bibliográficas e links; o YouTube fornece vídeos e suas durações. Na tela da turma,
a escolha de um resultado preenche o formulário de conteúdo, reduzindo a digitação.
O material só é salvo quando o formulário é enviado e aceito pela aplicação.

O Brevo é utilizado como serviço de e-mail por SMTP nos fluxos de verificação.
As operações HTTP expostas neste Swagger são as duas buscas do CampusLink.
O envio SMTP é documentado como integração complementar.

## 2. Componentes e fluxo

| Camada | Google Books | YouTube |
| --- | --- | --- |
| Interface | `templates/Geral/ambienteTurma.html` | `templates/Geral/ambienteTurma.html` |
| Controller | `GoogleBooksController` | `YoutubeController` |
| Service | `GoogleBooksService` | `YoutubeService` |
| Cliente HTTP | `googleBooksRestClient`, criado em `GoogleBooksConfig` | `youtubeRestClient`, criado em `YoutubeConfig` |
| Retorno à interface | `List<GoogleBookDTO>` | `List<YoutubeVideoDTO>` |

A interface envia uma requisição GET ao CampusLink com o termo de pesquisa.
O controller verifica a sessão; quando existe `usuarioLogado`, chama o service.
O service consulta o provedor com `RestClient`, transforma o JSON recebido em
DTOs e devolve a lista à interface. As chaves são acrescentadas pelo servidor.

Na inclusão de conteúdo, selecionar um livro preenche o link e, se vazio, o título.
Selecionar um vídeo preenche link, duração e, se vazio, título. Na edição, o livro
preenche o link; o vídeo preenche link e duração. A busca não grava esses dados
no banco por conta própria.

## 3. Contrato HTTP do CampusLink

| Método | Rota | Entrada | Retorno de sucesso |
| --- | --- | --- | --- |
| GET | `/googlebooks/buscar` | `q`, string obrigatória na query | Array JSON de até 10 livros |
| GET | `/youtube/buscar` | `q`, string obrigatória na query | Array JSON de até 10 vídeos |

Exemplos locais:

- [Buscar livros](http://localhost:8080/googlebooks/buscar?q=engenharia%20de%20software)
- [Buscar vídeos](http://localhost:8080/youtube/buscar?q=engenharia%20de%20software)

As duas operações não recebem corpo. O limite de 10 é definido nos services;
esta versão não expõe parâmetros de paginação ou de quantidade ao cliente.
O frontend aplica `trim()` e ignora a busca vazia. Os controllers exigem a
presença de `q`, mas não validam texto vazio ou composto por espaços quando
acessados diretamente.

### Sessão e perfis

Os controllers verificam `session.getAttribute("usuarioLogado")`. O navegador
reutiliza o cookie da sessão HTTP, normalmente chamado `JSESSIONID`.
Para testar pelo Swagger, é necessário concluir o login e a verificação por
e-mail no mesmo navegador, protocolo, domínio e porta. Por exemplo, não alternar
entre `localhost` e `127.0.0.1` durante esse teste.

Sem `usuarioLogado`, a implementação retorna **HTTP 200 e `[]`**, sem consultar
o provedor. Os dois controllers não verificam `tipoUsuario`; portanto, sua regra
de acesso é a presença do usuário na sessão. A seleção de materiais aparece nos
formulários destinados ao professor.

### Status e limitações atuais

| Situação | Comportamento implementado |
| --- | --- |
| Consulta concluída | `200`, com lista de resultados |
| Sem resultados úteis | `200`, com `[]` |
| Sem usuário logado | `200`, com `[]`, sem chamada externa |
| Parâmetro `q` ausente | `400`, gerado pelo Spring MVC |
| `q` vazio ou espaços | Sem rejeição específica no controller; o processamento continua conforme o provedor |
| Falha de rede, credencial, cota ou processamento que gere exceção não tratada | Erro `500` da aplicação; o status do provedor não é repassado diretamente |

Não há um DTO de erro ou tratamento específico dessas falhas nos dois fluxos.
O formato do corpo de erro segue a configuração padrão da aplicação.
Por isso, esta documentação não anuncia `401`, `403`, `429`, `502` ou `503`
como respostas específicas já implementadas nessas rotas.

## 4. Google Books API v1

O `GoogleBooksService` faz uma consulta a
`GET https://www.googleapis.com/books/v1/volumes` com os parâmetros:

| Parâmetro externo | Valor usado no projeto |
| --- | --- |
| `q` | Termo recebido pelo controller |
| `maxResults` | `10` |
| `key` | Valor de `googlebooks.api.key`, obtido de `GOOGLE_BOOKS_API_KEY` |

O service percorre `items`, descarta itens sem `volumeInfo` e monta os DTOs.
Se a resposta ou `items` for nulo, devolve uma lista vazia.

| Campo de `GoogleBookDTO` | Origem e transformação |
| --- | --- |
| `titulo` | `volumeInfo.title`; pode ser nulo |
| `autores` | `volumeInfo.authors`, unido por vírgulas; sem autores retorna `Autor desconhecido` |
| `editora` | `volumeInfo.publisher`; pode ser nula |
| `anoPublicacao` | Quatro primeiros caracteres de `publishedDate`; data ausente ou curta produz `""` |
| `isbn` | Busca primeiro `ISBN_13`, depois `ISBN_10`; sem ambos produz `""`; o valor do identificador pode ser nulo |
| `thumbnailUrl` | `volumeInfo.imageLinks.thumbnail`; pode ser nula |
| `linkGoogleBooks` | `volumeInfo.infoLink`; pode ser nulo |

A integração consulta metadados e links. Ela não baixa o conteúdo integral dos livros.

## 5. YouTube Data API v3

O `YoutubeService` executa duas etapas:

| Etapa | Requisição externa | Parâmetros usados |
| --- | --- | --- |
| Pesquisa | `GET https://www.googleapis.com/youtube/v3/search` | `part=snippet`, `type=video`, `maxResults=10`, `q` e `key` |
| Duração | `GET https://www.googleapis.com/youtube/v3/videos` | `part=contentDetails`, `id` com os identificadores separados por vírgulas e `key` |

A segunda chamada só ocorre quando a primeira fornece itens. Ambas utilizam
`youtube.api.key`, obtido de `YOUTUBE_API_KEY`. O service relaciona os detalhes
aos resultados pelo identificador do vídeo e converte a duração de ISO 8601 com
`java.time.Duration`.

| Campo de `YoutubeVideoDTO` | Origem e transformação |
| --- | --- |
| `videoId` | `id.videoId` da pesquisa; a interface monta `https://www.youtube.com/watch?v={videoId}` |
| `titulo` | `snippet.title` |
| `canal` | `snippet.channelTitle` |
| `thumbnailUrl` | `snippet.thumbnails.medium.url` |
| `duracao` | `contentDetails.duration`, convertida para `M:SS` ou `H:MM:SS`; pode ser nula se não houver detalhe ou vazia se a duração recebida for nula |

A integração consulta vídeos públicos e seus metadados. Ela não realiza upload
nem download do arquivo de vídeo.

## 6. Brevo SMTP

O `emailService` injeta `JavaMailSender` e usa `MimeMessage` e `MimeMessageHelper`
para montar o e-mail com versões HTML e texto. `enviarCodigo` é chamado nos
fluxos de login, cadastro e reenvio do código de verificação. O método utiliza
`@Async("emailTaskExecutor")`.

| Configuração | Valor ou variável do projeto |
| --- | --- |
| Servidor | `smtp-relay.brevo.com` |
| Porta | `2525` |
| Autenticação | `spring.mail.properties.mail.smtp.auth=true` |
| STARTTLS | Habilitado e obrigatório |
| Usuário SMTP | `BREVO_USERNAME`, usado em `spring.mail.username` |
| Credencial SMTP | `BREVO_PASSWORD`, usado em `spring.mail.password` |
| Remetente | Configurado em `emailService` e deve estar autorizado no Brevo |

O texto enviado informa que o código expira em cinco minutos; a emissão e a
validação do código pertencem ao fluxo de verificação da aplicação.
Como o envio é assíncrono, o retorno da página não comprova a entrega do e-mail.
Para evidenciar a integração, conferir também o recebimento na conta de teste.
Não existe no código uma chamada à API REST de envio do Brevo.

## 7. Configuração para executar

O projeto usa Java 25, Spring Boot 4.1.0 e
`springdoc-openapi-starter-webmvc-ui:3.1.1`, já declarados no `pom.xml` enviado.

As variáveis `GOOGLE_BOOKS_API_KEY`, `YOUTUBE_API_KEY`, `BREVO_USERNAME` e
`BREVO_PASSWORD` devem estar disponíveis para o processo da aplicação.
Configure-as no ambiente de execução do IntelliJ ou da hospedagem.
A disponibilidade de um arquivo de variáveis depende de como esse ambiente o
carrega; esta documentação não pressupõe carregamento automático de `.env`.

As configurações de banco e demais serviços necessárias para iniciar o CampusLink
continuam sendo as do projeto. Para as APIs Google, é necessário habilitar os
serviços correspondentes no projeto Google Cloud e usar chaves com as permissões
adequadas. As cotas e restrições aplicáveis são as da conta utilizada.

As propriedades de documentação já presentes são:

```properties
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs
springdoc.paths-to-match=/googlebooks/**,/youtube/**
```

## 8. Verificação funcional e evidências da entrega

1. Iniciar o projeto com as configurações de execução habituais.
2. Entrar como professor e concluir a verificação por e-mail.
3. Na turma, pesquisar um livro e um vídeo pelo tema `engenharia de software`.
4. Selecionar resultados e verificar os campos preenchidos no formulário.
5. Abrir o [Swagger UI local](http://localhost:8080/swagger-ui.html) no mesmo navegador e origem.
6. Executar as duas buscas usando **Try it out**, `q` e **Execute**.
7. Conferir `200` e itens reais em **Server response / Response body**. A área de exemplos não comprova consumo externo.
8. Se houver `[]`, conferir a sessão e tentar outro termo. Se houver `500`, examinar o erro da consulta no console do backend.
9. Conferir os campos e descrições em **Schemas**, os exemplos e as respostas documentadas em cada operação.
10. Para arquivar a especificação efetivamente gerada, abrir [OpenAPI JSON](http://localhost:8080/v3/api-docs) e salvar como `docs/openapi.json` no repositório.

Sugestão para o trecho das APIs no vídeo: mostrar uma busca de livro, uma busca
de vídeo, a seleção no formulário e uma resposta real no Swagger, explicando o
uso acadêmico e a comunicação realizada pelo backend. Reservar cerca de 60 a
90 segundos dentro dos cinco minutos totais destinados às quatro funcionalidades.

O comunicado da disciplina exige código publicado na branch `entrega2809` e
validação com o orientador até 27/09/2026. Em 28/09/2026 ocorre o envio do vídeo.

## 9. Referências técnicas

- [Google Books — volumes.list](https://developers.google.com/books/docs/v1/reference/volumes/list)
- [YouTube — search.list](https://developers.google.com/youtube/v3/docs/search/list)
- [YouTube — videos.list](https://developers.google.com/youtube/v3/docs/videos/list)
- [Brevo — integração SMTP](https://developers.brevo.com/docs/smtp-integration)
- [springdoc-openapi](https://springdoc.org/)
- [Swagger UI](https://swagger.io/open-source/swagger-ui/)

Os parâmetros, campos, regras de sessão e limitações acima foram extraídos do
código do CampusLink. As referências externas identificam os contratos dos
provedores e as ferramentas de documentação.
