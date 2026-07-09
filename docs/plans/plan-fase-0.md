# Fase 0 — Fundação do repositório + núcleo de conexão (`core`)

## Contexto & objetivo
Criar o esqueleto do projeto `sd-runner` em `/projects/sd-runner` e portar do `runner`
o mínimo para **conectar a um MySQL e rodar uma query com streaming**, exercitado por
uma pequena CLI. Sem GUI, sem persistência ainda. É a prova de que o núcleo reaproveitado
funciona isolado do "cérebro" (CareOS/STOMP).

## Pré-requisitos
- JDK 17, Maven (ou usar `mvnw` copiado do `runner`).
- Docker (para subir um MySQL de teste manualmente nesta fase).
- Acesso de leitura ao repo `runner` como referência de código.

## Escopo
**Inclui:** estrutura Maven multi-módulo; módulo `core`; CLI de smoke test.
**Não inclui:** GUI, noVNC, persistência de perfis, provisionador automático.

## Estrutura criada
```
sd-runner/
  pom.xml                      # parent (pom), grupo health.tabia.sdrunner
  mvnw, .mvn/                  # copiados do runner
  modules/
    core/
      pom.xml
      src/main/java/health/tabia/sdrunner/core/
        ConnectionEngine.java      # port de jpa/DatasourceEngine (Hikari + disposable)
        QueryRunner.java           # port de utils/QueryUtils (streaming/abort)
        QueryStatement.java        # port 1:1
        RunningQueries.java        # port de RunningQueriesService
        DriverCatalog.java         # port de utils/DriverUtils
        model/QueryResult.java     # port do dto/QueryResult
    cli/
      pom.xml
      src/main/java/health/tabia/sdrunner/cli/Main.java   # smoke test
```

## Passos de implementação
1. **Parent POM**: copiar propriedades de versão do `runner` (`pom.xml`): Java 17,
   Hikari (via spring-boot), `sqlite`, `mysql.version`. Remover plugins não usados
   (forbiddenapis pode ficar; enforcer opcional).
2. **Módulo `core`** com dependências: `spring-boot-starter-jdbc` (traz `DataSourceBuilder`
   + HikariCP), `mysql-connector-j` (scope `compile`), `jetbrains-annotations`, `lombok`,
   `guava`, `slf4j`.
3. **Portar `ConnectionEngine`** a partir de `jpa/DatasourceEngine.java`:
   - manter `createDisposableDataSource(url, user, pass, driver, type)` e o mapa
     `Map<id, Map<version, DataSource>>` (ou simplificar para `Map<profileId, DataSource>`);
   - remover o listener de chave de criptografia (entra na Fase 2).
4. **Portar `QueryRunner`** de `utils/QueryUtils.java`: manter `runQuery(...)` com
   `PreparedStatement`, parâmetros tipados, `limit`, modifying vs select, streaming via
   callbacks e sentinela de fim; manter integração com `RunningQueries` (abort).
5. **Portar `DriverCatalog`** (`DriverUtils`): enumerar `DriverManager` (retirar filtro do H2).
6. **CLI `Main`**: recebe `--url --user --pass --sql`, cria datasource descartável,
   roda a query e imprime cabeçalho + linhas no stdout (usando os callbacks do `QueryRunner`).

## Reuso do `runner` (mapa)
| Novo | Origem |
|---|---|
| `ConnectionEngine` | `modules/app/.../jpa/DatasourceEngine.java` |
| `QueryRunner` | `.../utils/QueryUtils.java` |
| `QueryStatement`, `RunningQueries` | `.../utils/QueryStatement.java`, `.../services/RunningQueriesService.java` |
| `DriverCatalog` | `.../utils/DriverUtils.java` |
| `model/QueryResult` | `.../dto/QueryResult.java` |

## Verificação (end-to-end)
1. Subir MySQL de teste: `docker run -d --name sdr-mysql -e MYSQL_ROOT_PASSWORD=dev -p 3306:3306 mysql:8`.
2. `mvn -pl modules/cli -am package` e executar a CLI:
   `java -jar modules/cli/target/cli.jar --url jdbc:mysql://localhost:3306 --user root --pass dev --sql "SELECT 1 AS ok"`.
3. **Aceite:** imprime `ok` = 1; uma query maior (ex.: `information_schema.tables`) faz streaming
   de várias linhas; `Ctrl-C`/abort encerra o statement sem travar.

## Riscos & decisões
- **Simplificar o versionamento** `(id, version)` do runner para `profileId` — decidir aqui.
- Manter Spring só pelo `DataSourceBuilder`/Hikari; se pesar, trocar por Hikari puro.
- Timezone/SSL do MySQL 8 na URL (`?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false` em dev).
