# sd-runner

Aplicação **standalone** de banco de dados: uma **GUI** (servida no navegador via **noVNC**) que
**provisiona** um ambiente MySQL (container Docker ou VM), **conecta** e **executa queries**, com
perfis de conexão **persistidos e cifrados** localmente.

Reaproveita os "tijolos" do projeto `runner` (pool JDBC, execução de query com streaming/abort,
criptografia de atributos, empacotamento Docker), trocando o "cérebro" remoto (CareOS/STOMP) por
uma **UI local** desbloqueada por uma **passphrase mestra**.

> Tema: **Docker + MySQL + persistência + X Window**.

---

## 1. Organização do projeto

Projeto Maven multi-módulo (Java 17). Cada módulo tem uma responsabilidade única:

```
sd-runner/
├── pom.xml                      # POM pai (versões, plugins, lista de módulos)
├── Makefile                     # atalhos: build, test, it, up, up-prod, down, image
├── README.md                    # este arquivo
├── docs/
│   └── plans/                   # planos de implementação por fase (0 a 4) + índice
├── docker/
│   ├── Dockerfile               # imagem multi-stage: build Maven + runtime com noVNC
│   ├── entrypoint.sh            # Xvfb → x11vnc → noVNC → app (senha/TLS opcionais)
│   ├── docker-compose.yml       # stack de DEV (auto-unlock, MySQL embutido, DooD)
│   └── docker-compose.prod.yml  # stack de PROD (senha VNC, sem docker.sock)
├── tools/
│   ├── build-image.sh           # build/push da imagem
│   └── k8s.yaml                 # Deployment + Service + PVC de exemplo
└── modules/
    ├── core/          # pools (HikariCP), execução de query com streaming/abort, catálogo de drivers
    ├── cli/           # smoke-test por linha de comando (conecta e roda 1 SQL) — uber-jar
    ├── store/         # persistência SQLite + Encryptor + MasterKeyManager (chave por passphrase)
    ├── provisioner/   # DockerMysqlProvisioner (container+volume+seed) e VmMysqlProvisioner (cloud-init)
    └── ui/            # GUI Swing: conexões, editor SQL, resultados, "Novo ambiente MySQL" — uber-jar
```

Dependências entre módulos: `cli → core`; `store → core`; `provisioner → core`;
`ui → core, store, provisioner`.

Mapa fase → entrega (detalhes em `docs/plans/`):

| Fase | Módulo/artefato | O que entrega |
|------|-----------------|---------------|
| 0 | `core`, `cli` | Núcleo de conexão/consulta + CLI |
| 1 | `ui`, `docker/` | GUI Swing via noVNC no container |
| 2 | `store` | Perfis persistidos e cifrados (passphrase mestra) |
| 3 | `provisioner` (Docker) | Sobe MySQL container com volume + seed |
| 4 | `provisioner` (VM) + `docker-compose.prod.yml` | Modo VM (cloud-init) + hardening + empacotamento |

---

## 2. Pré-requisitos

- **JDK 17** e **Maven 3.6+**
- **Docker** e **Docker Compose v2** (para a GUI/noVNC e o provisionamento)
- Navegador (para acessar a GUI via noVNC)

---

## 3. Passo a passo de inicialização

### 3.1. Build e testes
```bash
cd ~/projects/sd-runner

make build        # compila todos os módulos + testes unitários
# (equivale a: mvn -B -DskipTests=false install)
```

### 3.2. Rodar a GUI (modo DEV, mais simples)
Sobe a app (noVNC) + um MySQL de teste com volume persistente. Em DEV a app
**auto-desbloqueia** (via `SD_MASTER_PASSPHRASE`) e o noVNC fica **sem senha**.
```bash
make up
# abra no navegador:
#   http://localhost:6080/vnc.html
```
Na GUI: **Nova** (cria um perfil, ex.: host `mysql`, porta `3306`, user `root`, senha `dev`) →
**Testar conexão** → **Conectar** → escreva um SQL e **Executar (F5)**.
Para provisionar um banco novo do zero: **Novo ambiente MySQL**.

Derrubar:
```bash
make down
```

### 3.3. Rodar em modo PROD (endurecido)
Exige **senha no noVNC** e **não** faz auto-unlock (você digita a passphrase mestra na GUI).
O `docker.sock` **não** é montado (sem DooD).
```bash
VNC_PASSWORD='troque-esta-senha' make up-prod
# acesse http://localhost:6080/vnc.html e informe a senha do VNC
```

### 3.4. Usar a CLI (sem GUI)
```bash
make cli   # gera modules/cli/target/sd-runner-cli.jar

java -jar modules/cli/target/sd-runner-cli.jar \
  --url 'jdbc:mysql://localhost:3307/sdr_demo?allowPublicKeyRetrieval=true&useSSL=false' \
  --user root --pass dev \
  --sql 'SELECT 1 AS ok'
# flags: --modifying (para DDL/DML), --limit N, --driver <classe>
```

### 3.5. Teste de integração do provisionador (Docker real)
```bash
make it
# provisiona um MySQL em container, aplica seed, lê as linhas e limpa ao final
```

### 3.6. Modo VM (opcional)
Gera o `cloud-init` automaticamente; o lançamento da VM é delegado a um comando configurável:
```bash
export SD_VM_LAUNCHER='multipass launch --name {name} --cloud-init {cloudinit} 22.04 && \
  multipass info {name} --format csv | tail -1 | cut -d, -f3'
# {cloudinit} = caminho do arquivo gerado, {name} = nome do ambiente; deve imprimir o IP na última linha
```

### 3.7. Empacotar/publicar a imagem
```bash
tools/build-image.sh 0.1.0            # build local
tools/build-image.sh 0.1.0 --push     # build + push (SD_IMAGE_REPO define o registry)
```

---

## 4. Como funciona (resumo técnico)

- **Conexão/consulta** (`core`): pools HikariCP; `QueryRunner` executa `PreparedStatement` e faz
  **streaming** das linhas via callbacks; execuções ficam registradas para permitir **abort**.
- **Persistência** (`store`): SQLite local; senhas dos perfis **cifradas em repouso**
  (PBE + HMAC-SHA256 + AES-128). A **chave vive só em memória**, derivada da passphrase mestra
  (`MasterKeyManager`). Um "verificador" valida a passphrase na abertura.
- **Provisionamento** (`provisioner`): `DockerMysqlProvisioner` cria container MySQL com
  **volume nomeado** (persistência), espera readiness e aplica o **seed**; `VmMysqlProvisioner`
  gera `cloud-init` e delega o boot da VM a um launcher configurável.
- **GUI/X Window** (`ui` + `docker/`): a app Swing roda sobre um **Xvfb**; **x11vnc** expõe a tela
  e **noVNC/websockify** entrega no navegador em `:6080`.

## 5. Configuração por variáveis de ambiente

| Variável | Efeito |
|---|---|
| `SD_MASTER_PASSPHRASE` | Auto-desbloqueia o store (headless/demo). Em prod, **não** definir. |
| `STATE_DIR` | Pasta do `app.db` (padrão `~/.sd-runner`; no container `/app/state`). |
| `GEOMETRY` | Resolução do Xvfb (padrão `1280x800x24`). |
| `VNC_PASSWORD` | Habilita senha no x11vnc (obrigatória no compose de prod). |
| `CERT_FILE` | Se definido, o noVNC/websockify serve com **TLS**. |
| `SD_VM_LAUNCHER` / `SD_VM_STOP` / `SD_VM_STATUS` | Comandos do modo VM. |
| `SD_IT_DOCKER=1` | Habilita o teste de integração do provisionador. |

## 6. Segurança (avisos)

- **DooD** (montar `docker.sock`) é usado **só em DEV** para o botão "Novo ambiente"; dá acesso
  amplo ao host — **desabilitado em PROD** (use o modo VM).
- Em PROD, o noVNC exige senha (`VNC_PASSWORD`) e pode usar TLS (`CERT_FILE`). Nunca exponha o
  noVNC sem autenticação.
- Perder a passphrase mestra = perder o acesso aos segredos (por design).
