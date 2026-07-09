# Tutorial — CLI (básico)

Objetivo: usar a **CLI de smoke-test** para conectar a um MySQL via JDBC e rodar **um**
comando SQL, imprimindo o resultado — sem abrir a GUI. É a forma mais rápida de
validar conectividade e uma query.

A CLI vem do módulo `cli` (`modules/cli`) e é empacotada como um **uber-jar**.

---

## Pré-requisitos

- JDK 17
- Um MySQL acessível. O jeito mais simples é subir o MySQL da stack de DEV:

  ```bash
  cd ~/projects/sd-runner
  make up
  ```

  Isso publica o MySQL de teste em **`localhost:3307`** (database `sdr_demo`,
  root/`dev`). A CLI roda na **sua máquina**, então use `localhost:3307`
  (a porta publicada), e não `mysql:3306`.

---

## Passo 1 — Compilar o uber-jar da CLI

```bash
cd ~/projects/sd-runner
make cli
```

Equivale a `mvn -B -pl modules/cli -am -DskipTests package` e gera:

```
modules/cli/target/sd-runner-cli.jar
```

## Passo 2 — Rodar um SELECT

```bash
java -jar modules/cli/target/sd-runner-cli.jar \
  --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
  --user root --pass dev \
  --sql 'SELECT 1 AS ok'
```

Saída esperada (cabeçalho, linhas, e um rodapé com a contagem):

```
ok
1
-- done (1 row(s)) --
```

## Entendendo as flags

| Flag | Obrigatória? | Descrição |
|------|--------------|-----------|
| `--url`  | **sim** | URL JDBC completa (inclui host, porta, database e parâmetros) |
| `--sql`  | **sim** | O comando SQL a executar (apenas **um**) |
| `--user` | não (padrão vazio) | Usuário do banco |
| `--pass` | não (padrão vazio) | Senha do banco |
| `--driver` | não | Classe do driver JDBC (autodetectada pela URL na maioria dos casos) |
| `--modifying` | não (flag) | Marca a query como DDL/DML — ver [cli-avancado](../cli-avancado/README.md) |
| `--limit N` | não | Limita o número de linhas lidas |

> A URL de exemplo usa `allowPublicKeyRetrieval=true&useSSL=false` porque o MySQL 8 de
> DEV não tem TLS configurado; em bancos reais ajuste os parâmetros de acordo.

## Código de saída

A CLI retorna:

- **0** — query executada com sucesso
- **1** — a query rodou mas falhou (erro impresso em `stderr` como `ERROR: ...`)
- **2** — faltou uma flag obrigatória (`--url` ou `--sql`)

Útil para usar em scripts:

```bash
if java -jar modules/cli/target/sd-runner-cli.jar \
     --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
     --user root --pass dev --sql 'SELECT 1'; then
  echo "banco OK"
else
  echo "banco indisponível (exit $?)"
fi
```

---

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---------|----------------|-------------|
| `Missing required --url` (exit 2) | Esqueceu `--url`/`--sql` | Informe as duas flags obrigatórias |
| `Communications link failure` | MySQL não está no ar / porta errada | `make up`; use `localhost:3307` |
| `Public Key Retrieval is not allowed` | Faltou parâmetro na URL | Acrescente `allowPublicKeyRetrieval=true&useSSL=false` |
| `No suitable driver` | URL não reconhecida | Confira o esquema `jdbc:mysql://...` ou passe `--driver` |

## Próximos passos

- Rodar DDL/DML, limitar linhas, provisionar e empacotar → [cli-avancado](../cli-avancado/README.md)
- Fazer o mesmo pela interface → [gui-basico](../gui-basico/README.md)
