# Testes automatizados

Os novos testes de eventos, disponibilidade, denúncias e administração ficam
nesta pasta, seguindo os pacotes Java a partir de `com/example/CampusLink/`.

O `pom.xml` registra `tests/` como pasta adicional de código de teste por meio
do `build-helper-maven-plugin`. Os testes existentes em `src/test/java` continuam
incluídos na compilação e na execução pelo Maven.

Para executar todos os testes, incluindo os existentes, na raiz do módulo `CampusLink`:

```powershell
.\mvnw.cmd clean test
```

Requer JDK 25. Na primeira execução, o Maven baixa as dependências e os binários
do PostgreSQL embarcado; as execuções seguintes reutilizam o cache local.

Os testes unitários usam JUnit e Mockito. Os testes de integração sobem o contexto
Spring e um PostgreSQL real, temporário, em porta local livre, usando
[Zonky Embedded Postgres](https://github.com/zonkyio/embedded-postgres).
Não é necessário instalar Docker ou configurar um banco manualmente.

`resources/application.properties` fornece a configuração exclusiva de testes,
sem importar `.env`. `BancoTesteInitializer` configura o banco descartável e
carrega `resources/schema-test.sql`. Os cenários de integração recriam seus
dados antes de cada teste; os testes legados de DAO usam suas tabelas temporárias.
O processo PostgreSQL é encerrado ao finalizar a JVM de testes. A suíte pressupõe
a execução sequencial padrão do Maven, pois compartilha essa instância temporária.

Para executar somente as integrações novas:

```powershell
.\mvnw.cmd -Dtest=FluxosIntegrationTest test
```

Para executar somente um grupo unitário:

```powershell
.\mvnw.cmd -Dtest=DenunciaServiceTest test
```

Os relatórios de cada execução são gerados pelo Maven em
`target/surefire-reports/`. Essa pasta é recriada ao executar os testes e não
precisa ser incluída no commit.

Alguns cenários simulam exceções propositalmente e produzem mensagens `ERROR` no
log. O resultado do teste é determinado pelas asserções e pelo resumo Surefire,
não pela quantidade dessas mensagens.
