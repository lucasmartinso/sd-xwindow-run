# Tutorial — CLI (avançado)

Objetivo: ir além do smoke-test do [cli-basico](../cli-basico/README.md) — rodar
**DDL/DML** (`--modifying`), **limitar linhas** (`--limit`), forçar um **driver**
específico, provisionar bancos via **teste de integração**, usar o **modo VM**
(cloud-init + launcher configurável) e **empacotar/publicar** a imagem noVNC.

---

## 1. Rodar DDL/DML com `--modifying`

A CLI executa **um** statement. Para comandos que **modificam** o banco
(`CREATE`, `INSERT`, `UPDATE`, `DELETE`, `DROP`...) passe a flag `--modifying`:

```bash
java -jar modules/cli/target/sd-runner-cli.jar \
  --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
  --user root --pass dev \
  --modifying \
  --sql 'CREATE TABLE t (id INT PRIMARY KEY, v VARCHAR(16))'
```

Depois, uma leitura normal (sem `--modifying`):

```bash
java -jar modules/cli/target/sd-runner-cli.jar \
  --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
  --user root --pass dev \
  --sql 'INSERT INTO t VALUES (1,"a")' --modifying
```

> Diferente da GUI (que **infere** leitura vs. modificação pelo prefixo do SQL), na
> CLI você **declara** explicitamente com `--modifying`. Sem a flag, o statement é
> executado como uma query de leitura.

## 2. Limitar linhas com `--limit`

Para não trazer tabelas inteiras ao testar:

```bash
java -jar modules/cli/target/sd-runner-cli.jar \
  --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
  --user root --pass dev \
  --limit 10 \
  --sql 'SELECT * FROM information_schema.tables'
```

As linhas são lidas em **streaming** e o processamento para ao atingir o limite.

## 3. Forçar um driver com `--driver`

Normalmente o driver é autodetectado pela URL JDBC. Para forçar (ex.: driver
alternativo no classpath):

```bash
  --driver com.mysql.cj.jdbc.Driver
```

## 4. Teste de integração do provisionador (Docker real)

Provisiona um MySQL em container de verdade, aplica seed, lê as linhas e **limpa ao
final**. Requer Docker no ar:

```bash
cd ~/projects/sd-runner
make it
```

Equivale a:

```bash
SD_IT_DOCKER=1 mvn -B -pl modules/provisioner test \
  -Dtest=DockerMysqlProvisionerIT -Dsurefire.failIfNoSpecifiedTests=false
```

A variável `SD_IT_DOCKER=1` é o gatilho que habilita esse IT (por padrão ele é
pulado). Um `EnvironmentSpec` define o ambiente: `name`, `mysqlVersion`, `hostPort`
(`0` = porta livre automática), `rootPassword`, `database` e `seedSql`.

## 5. Modo VM {#modo-vm}

O `VmMysqlProvisioner` **gera o cloud-init** automaticamente e delega o boot da VM a
um **comando configurável** — assim funciona com multipass, vagrant, CLI de nuvem,
libvirt etc. Sem `SD_VM_LAUNCHER` definido, o modo VM falha com orientação (nenhum
hypervisor é assumido).

Placeholders substituídos no comando: `{cloudinit}` (caminho do arquivo gerado) e
`{name}` (nome do ambiente). O launcher **deve imprimir o IP da VM na última linha**
do stdout.

Exemplo com **multipass**:

```bash
export SD_VM_LAUNCHER='multipass launch --name {name} --cloud-init {cloudinit} 22.04 && \
  multipass info {name} --format csv | tail -1 | cut -d, -f3'
```

Comandos opcionais do ciclo de vida (também usam `{name}`):

| Variável | Uso |
|----------|-----|
| `SD_VM_LAUNCHER` | Sobe a VM e imprime o IP (obrigatória para o modo VM) |
| `SD_VM_STOP` | Para a VM |
| `SD_VM_REMOVE` | Remove a VM |
| `SD_VM_STATUS` | Retorna não-vazio se a VM está rodando (`isRunning`) |

Após o boot, o provisionador **aguarda o MySQL ficar pronto** (até ~300s), conectando
como `root` na porta `3306` do IP retornado. É a alternativa recomendada ao DooD em
ambientes endurecidos (PROD).

## 6. Empacotar e publicar a imagem noVNC

Build local da imagem de runtime (Xvfb → x11vnc → noVNC → app):

```bash
tools/build-image.sh 0.1.0
```

Build + push para o registry:

```bash
tools/build-image.sh 0.1.0 --push
```

O registry padrão é `ghcr.io/tabiahealth/sd-runner` e pode ser trocado via
`SD_IMAGE_REPO`:

```bash
SD_IMAGE_REPO='meu-registry.example.com/sd-runner' tools/build-image.sh 0.1.0 --push
```

> `make image` também dispara `tools/build-image.sh` (com a tag padrão `latest`).
> Fazer **push** publica a imagem num serviço externo — confirme registry e
> credenciais antes.

## 7. Rodar a app diretamente (uber-jar da UI, sem Docker)

Para testar a app fora do container (precisa de um display X local ou headless
adequado), com auto-unlock e um `STATE_DIR` isolado:

```bash
SD_MASTER_PASSPHRASE=devpass STATE_DIR=/tmp/sdr-state \
  java -jar modules/ui/target/sd-runner-app.jar
```

Sem `SD_MASTER_PASSPHRASE`, a app abre o diálogo de passphrase mestra
(ver [gui-avancado](../gui-avancado/README.md#2-definir-e-usar-a-passphrase-mestra)).

---

## Referência rápida de variáveis (CLI/provisionamento)

| Variável | Efeito |
|----------|--------|
| `SD_IT_DOCKER=1` | Habilita o teste de integração do provisionador Docker |
| `SD_PROVISION_NETWORK` | Rede Docker à qual anexar ambientes provisionados (necessária quando a app roda dentro de um container, como na GUI de DEV). No host (CLI) deixe **sem** definir: usa `127.0.0.1` + porta publicada |
| `SD_VM_LAUNCHER` / `SD_VM_STOP` / `SD_VM_REMOVE` / `SD_VM_STATUS` | Comandos do modo VM |
| `SD_IMAGE_REPO` | Registry/repositório da imagem no `build-image.sh` |
| `SD_MASTER_PASSPHRASE` | Auto-desbloqueia o store (headless/demo) |
| `STATE_DIR` | Pasta do `app.db` (padrão `~/.sd-runner`) |

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---------|----------------|-------------|
| IT pulado / "No tests were executed" | Faltou o gatilho | Rode com `SD_IT_DOCKER=1` (é o que `make it` faz) |
| `VM mode requires SD_VM_LAUNCHER` | Launcher não configurado | `export SD_VM_LAUNCHER='...'` que imprime o IP na última linha |
| `Launcher did not return a VM IP` | Comando não imprimiu o IP por último | Garanta que a última linha do stdout seja só o IP |
| `denied` no push da imagem | Sem login no registry | Autentique-se no registry antes de `--push` |

## Próximos passos

- Fazer o mesmo pela interface → [gui-avancado](../gui-avancado/README.md)
- Voltar ao índice → [tutorials](../README.md)
