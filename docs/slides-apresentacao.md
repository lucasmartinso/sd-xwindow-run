# sd-runner — Estrutura de Slides (para PowerPoint / Google Slides)

> **Tema:** Docker + MySQL + persistência + X Window
> **Contexto:** apresentação acadêmica · ~20 min · ~14 slides
> **Direção visual:** *Blueprint técnico* (esquemático de engenharia)

Este documento descreve **slide a slide**: o título, o conteúdo (com os detalhes
técnicos), o **visual sugerido** (diagramas que você recria no editor) e uma
**nota do apresentador** (o que falar). No fim há uma *colinha* de como montar os
diagramas com as formas nativas do PowerPoint.

---

## 🎨 Guia visual — "Blueprint técnico"

Aplique este sistema em **todos** os slides para dar unidade. A ideia é evocar um
**esquema de engenharia**: fundo escuro ardósia, uma **grade fininha** ao fundo,
rótulos em **fonte monoespaçada** (como código) e títulos em **sans forte**.

### Paleta (cole estes hex no tema do PowerPoint)

| Papel | Nome | Hex | Uso |
|-------|------|-----|-----|
| Fundo | Ardósia-tinta | `#0E1726` | fundo de todos os slides |
| Painel | Ardósia-painel | `#16223A` | caixas/cards sobre o fundo |
| Grade | Azul-grade | `#22304C` | linhas da grade (bem sutis, ~15% opacidade) |
| Texto | Névoa-clara | `#E7ECF5` | títulos e texto principal |
| Texto 2 | Azul-acinzentado | `#8FA3C0` | rótulos, legendas, texto secundário |
| **Acento 1** | **Azul-container** | `#38A6E0` | Docker, X Window, destaques principais |
| **Acento 2** | **Âmbar-dados** | `#E6A23C` | MySQL, persistência, "dados" |
| Acento 3 | Verde-terminal | `#5FB57A` | sucesso, comandos, "pronto/ok" |
| Alerta | Coral | `#E06A5A` | avisos de segurança, trade-offs |

> **Regra de cor:** azul = *plataforma/execução*; âmbar = *dados/persistência*;
> coral = *cuidado/segurança*. Mantenha essa semântica o tempo todo — ajuda o
> público a "ler" os diagramas sem legenda.

### Tipografia

- **Títulos:** sans forte e condensada — *Inter*, *Archivo* ou *Barlow* (Bold/SemiBold).
- **Rótulos, números de slide, código, nomes de componentes:** **monoespaçada** —
  *JetBrains Mono*, *Cascadia Code* ou *Consolas*. É o que dá o ar de "sistema".
- **Corpo:** a mesma sans dos títulos, peso Regular.
- **Eyebrow** (rótulo acima do título): mono, MAIÚSCULAS, espaçamento entre letras
  aumentado, na cor Azul-acinzentado. Ex.: `02 · X WINDOW`.

### Motivos de layout (repita em todo slide)

- **Grade de fundo** sutil (linhas a cada ~40px, cor Azul-grade, ~12–15% opacidade).
- **Numeração** do bloco no canto (`01`…`06`) em mono — só porque a apresentação
  **é** uma sequência real de temas.
- **Cards com borda fina** (1px, Azul-grade) e cantos levemente arredondados (4–6px),
  em vez de sombras pesadas. Blueprint = traço fino, não volume.
- **Setas** sempre finas com ponta triangular pequena; nada de setas 3D.
- **Código** em caixa Ardósia-painel, fonte mono, com realce de palavra-chave em Azul/Âmbar.

---

# 🖥️ Os slides

---

## Slide 1 — Capa

**Eyebrow:** `PROJETO · SISTEMAS DISTRIBUÍDOS`

**Título:** **sd-runner**

**Subtítulo:** Uma GUI de banco de dados que se **provisiona**, **conecta** e
**persiste** — dentro de um container, entregue no navegador.

**Rodapé (mono, pequeno):** `Docker · MySQL · Persistência · X Window` — Autor · Disciplina · Data

**Visual:** fundo blueprint com a grade visível. Ao centro/direita, um **diagrama-teaser**
minimalista da cadeia: quatro ícones-caixa ligados por setas — 🐳 Docker → 🗄️ MySQL →
🔒 Persistência → 🪟 X Window. (Use só contornos finos, cor de acento em cada um.)

**Nota do apresentador:** "Vou apresentar o sd-runner, um projeto que amarra os quatro
temas do trabalho num problema real: entregar uma aplicação gráfica de banco de dados
sem instalar nada na máquina do usuário, com dados e segredos preservados."

---

## Slide 2 — O problema (gancho)

**Eyebrow:** `00 · MOTIVAÇÃO`

**Título:** Como entregar uma app **gráfica** de banco sem instalar nada?

**Conteúdo (3 tensões, em cards lado a lado):**
- 🪟 **Interface gráfica** — a app é **Swing** (desktop). Precisa de tela e servidor X…
  mas queremos rodá-la num **container headless** (sem monitor).
- 🗄️ **Ambiente de banco** — o usuário nem sempre tem um MySQL pronto. E se a própria
  app **criasse** o banco do zero?
- 🔒 **Dados e segredos** — perfis de conexão têm senhas. Precisam **sobreviver a
  reinícios** e ficar **cifrados**.

**Visual:** três cards escuros com ícone de acento, título e uma frase. Abaixo, uma
faixa: *"sd-runner resolve os três com Docker + MySQL + persistência + X Window."*

**Nota:** "Cada tema do trabalho não aparece isolado — eles se conectam para responder
essa pergunta. Guardem a imagem da app Swing rodando **sem monitor** e aparecendo numa
aba do navegador; é para lá que vamos."

---

## Slide 3 — Os 4 pilares (mapa da apresentação)

**Eyebrow:** `AGENDA`

**Título:** Quatro pilares, um sistema

**Visual:** grade 2×2 de cards, cada um com sua cor semântica:

| | |
|---|---|
| 🐳 **Docker** (azul) — empacotamento multi-stage; a app orquestra containers | 🗄️ **MySQL** (âmbar) — provisionamento automático: container ou VM |
| 🔒 **Persistência** (âmbar) — volume dos dados + perfis cifrados | 🪟 **X Window** (azul) — Swing headless entregue no navegador |

**Nota:** "Vou percorrer nesta ordem: primeiro a arquitetura, depois X Window (a parte
mais incomum), então Docker + MySQL, e por fim persistência. Fechando com o fio que
costura tudo: o trade-off entre praticidade e segurança."

---

## Slide 4 — Arquitetura: Maven multi-módulo

**Eyebrow:** `01 · ARQUITETURA`

**Título:** Cinco módulos, uma responsabilidade cada

**Conteúdo (lista mono, à esquerda):**
- `core` — pools JDBC (**HikariCP**), execução de query com **streaming** e **abort**
- `store` — persistência **SQLite** + criptografia (passphrase mestra)
- `provisioner` — cria MySQL em **container** ou **VM**
- `ui` — GUI **Swing**: conexões, editor SQL, resultados
- `cli` — smoke-test por linha de comando (uber-jar)

**Visual (à direita):** **grafo de dependência** com caixas e setas:
```
        ui
      ╱  │  ╲
  store  │  provisioner
      ╲  │  ╱
        core        cli ──▶ core
```
Legenda: seta = "depende de". `ui → core, store, provisioner`; todos apoiam-se no `core`.

**Nota:** "É um projeto Maven multi-módulo em Java 17. Essa separação é o fio condutor:
cada pilar da apresentação corresponde a um módulo. Repare que tudo se apoia no `core`,
o núcleo de conexão e consulta."

---

## Slide 5 — X Window: o problema

**Eyebrow:** `02 · X WINDOW`

**Título:** Uma app de **desktop**… dentro de um container **sem tela**

**Conteúdo (contraste em duas colunas):**
- **O que temos:** interface **Swing** — precisa de um **servidor gráfico X** e,
  normalmente, de um **monitor** e de um `DISPLAY`.
- **O que queremos:** rodar num **container headless** e acessar **pelo navegador**,
  sem instalar cliente nenhum.

**Visual:** à esquerda, ícone de janela com um "❌ sem monitor"; à direita, um navegador
com "✅". Entre eles, um ponto de interrogação grande — que o próximo slide responde.

**Nota:** "Aqui está o desafio central do tema X Window: conciliar uma GUI de desktop
com um ambiente que, por definição, não tem tela. A resposta é montar uma pequena
**pilha X** dentro do container."

---

## Slide 6 — X Window: a cadeia (slide-chave)

**Eyebrow:** `02 · X WINDOW`

**Título:** A cadeia: da janela Swing até o navegador

**Visual — PIPELINE HORIZONTAL (o diagrama mais importante do deck):**

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌───────────────┐   ┌────────────┐
│  App     │──▶│  Xvfb    │──▶│  x11vnc  │──▶│ noVNC +       │──▶│  Navegador │
│  Swing   │   │ (tela    │   │ (expõe   │   │ websockify    │   │  :6080     │
│          │   │ virtual  │   │ via VNC) │   │ (VNC→WebSocket│   │ /vnc.html  │
│ DISPLAY  │   │ na RAM)  │   │ :5900    │   │  na porta     │   │            │
│  =:99    │   │          │   │          │   │  6080)        │   │            │
└──────────┘   └──────────┘   └──────────┘   └───────────────┘   └────────────┘
   Swing          Xvfb          x11vnc            noVNC              browser
```

**Legenda de cada elo (bullets curtos abaixo do diagrama):**
- **Xvfb** — *X virtual framebuffer*: um servidor X que "desenha" numa tela **em memória**,
  sem monitor físico. É onde a app pensa que está sendo exibida (`DISPLAY=:99`).
- **fluxbox** — gerenciador de janelas leve (para as janelas se comportarem).
- **x11vnc** — captura essa tela virtual e a expõe por **VNC** (porta 5900).
- **noVNC + websockify** — traduzem VNC → **WebSocket**, entregando no **navegador** (`:6080`).

**Nota:** "Essa é a espinha do tema X Window. A app Swing desenha numa tela virtual (Xvfb);
o x11vnc publica essa tela; e o noVNC a leva para o navegador. O usuário abre
`localhost:6080/vnc.html` e usa a aplicação sem instalar absolutamente nada."

---

## Slide 7 — X Window: o entrypoint.sh

**Eyebrow:** `02 · X WINDOW`

**Título:** A ordem de partida do container

**Visual:** bloco de **código anotado** (caixa Ardósia-painel, fonte mono), com
comentários em Azul-acinzentado à direita:

```bash
export DISPLAY=:99

Xvfb :99 -screen 0 1280x800x24 &          # 1) tela virtual na memória
for i in $(seq 1 30); do                  #    espera a tela ficar pronta
  xdpyinfo -display :99 && break; sleep 0.3
done

fluxbox &                                 # 2) gerenciador de janelas
x11vnc -display :99 -rfbport 5900 ...  &  # 3) expõe a tela via VNC
websockify --web=/usr/share/novnc 6080 localhost:5900 &   # 4) ponte web

exec java -jar /app/sd-runner-app.jar     # 5) sobe a app Swing
```

**Destaque (callout coral):** o loop com `xdpyinfo` é um **readiness check** — só segue
quando a tela está pronta. Padrão que reaparece no provisionamento do MySQL.

**Nota:** "Esse script é a sequência de boot do container e materializa o diagrama anterior:
sobe a tela, espera ela ficar pronta, publica por VNC, faz a ponte web e só então executa a
aplicação. Guardem a ideia de *esperar ficar pronto* — ela volta no MySQL."

---

## Slide 8 — Docker: imagem multi-stage

**Eyebrow:** `03 · DOCKER`

**Título:** Uma imagem enxuta com **multi-stage build**

**Visual — DOIS ESTÁGIOS lado a lado, com uma seta "copia só o .jar":**

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  STAGE 1 · build            │        │  STAGE 2 · runtime           │
│  maven:3.9-temurin-17       │        │  eclipse-temurin:17-jre      │
│                             │        │                              │
│  mvn -pl modules/ui -am     │        │  + xvfb x11vnc fluxbox       │
│      package                │ ──jar─▶│  + novnc websockify          │
│                             │        │  + Docker CLI (p/ DooD)      │
│  → sd-runner-app.jar        │        │  COPY sd-runner-app.jar      │
└─────────────────────────────┘        └──────────────────────────────┘
   (Maven + código-fonte             (só o necessário para RODAR —
    ficam AQUI, descartados)          imagem final menor e mais segura)
```

**Conteúdo (bullets):**
- **Stage de build:** compila só a `ui` e suas dependências (`-pl modules/ui -am`).
- **Stage de runtime:** parte de um **JRE** e instala apenas o que roda a app + a pilha X.
- **Ganho:** Maven e código-fonte **não vão** para a imagem final → **menor e mais segura**.

**Nota:** "O multi-stage é um padrão central do Docker. Compilamos num estágio 'gordo'
com o Maven e copiamos **apenas o .jar** para um estágio de runtime enxuto. O usuário
final recebe uma imagem pequena, sem toolchain de build dentro."

---

## Slide 9 — MySQL: provisionamento automático

**Eyebrow:** `04 · MYSQL`

**Título:** A app **cria** o banco — em container ou em VM

**Visual — dois caminhos a partir do `provisioner`:**

```
                    ┌──────────────────────────────┐
                    │        provisioner           │
                    └───────────┬──────────┬────────┘
              DockerMysql…       │          │      VmMysql…
                    ▼                              ▼
   ┌───────────────────────────┐    ┌───────────────────────────┐
   │ Container MySQL           │    │ VM (cloud-init)           │
   │ • volume nomeado (persist)│    │ • gera cloud-init         │
   │ • espera readiness        │    │ • delega boot a launcher  │
   │ • aplica seed inicial     │    │   configurável (multipass)│
   └───────────────────────────┘    └───────────────────────────┘
```

**Conteúdo:**
- **`DockerMysqlProvisioner`** — sobe um **container MySQL** com **volume nomeado**
  (persistência), **espera o banco ficar pronto** (readiness) e aplica o **seed**.
- **`VmMysqlProvisioner`** — gera um arquivo **`cloud-init`** e delega o boot da VM a um
  **launcher configurável** (ex.: `multipass`).

**Nota:** "O sd-runner não só conecta num MySQL existente — ele provisiona um do zero.
São dois modos: um container Docker (rápido, para dev) ou uma VM via cloud-init (mais
isolada, para produção). Repare no *readiness*: mesma ideia do Xvfb."

---

## Slide 10 — MySQL: DooD e o trade-off

**Eyebrow:** `04 · MYSQL · SEGURANÇA`

**Título:** Como o container cria **outro** container? (DooD)

**Visual:** o container da app com uma seta saindo dele até o **`docker.sock` do host**,
e desse socket nascendo um **container MySQL irmão**. Marque a seta do socket com um
selo **coral "só em DEV"**.

```
  [ container sd-runner ] ──(docker CLI)──▶ /var/run/docker.sock (HOST)
                                                     │
                                                     ▼
                                          [ container MySQL irmão ]
```

**Conteúdo:**
- **DooD (Docker-out-of-Docker):** montamos o `docker.sock` do host; a app usa o **Docker
  CLI** para criar um container **irmão** de MySQL.
- ⚠️ **Trade-off honesto:** montar o `docker.sock` dá **acesso amplo ao host**. Por isso é
  usado **só em DEV**. Em **PROD**, desabilitado → usa-se o **modo VM**.

**Nota:** "Esse é um ponto que costuma gerar pergunta. Para o botão 'Novo ambiente' funcionar,
o container precisa falar com o Docker do host, via `docker.sock`. É poderoso, mas perigoso —
por isso fica restrito ao desenvolvimento; em produção, o caminho é a VM."

---

## Slide 11 — Persistência: dois níveis

**Eyebrow:** `05 · PERSISTÊNCIA`

**Título:** Dois níveis que costumam ser confundidos

**Visual:** dois cards âmbar lado a lado:

- 🗄️ **Dados do banco** — o container MySQL usa **volume nomeado** → os dados
  **sobrevivem** a reinícios/recriações do container.
- 🔑 **Perfis de conexão** — host, porta, usuário, senha ficam num **SQLite local**;
  as **senhas são cifradas em repouso**.

**Nota:** "É importante separar os dois: uma coisa é persistir os **dados** do MySQL — isso
é o volume Docker. Outra é persistir os **perfis de conexão** da própria ferramenta — e aí
entra criptografia, o próximo slide."

---

## Slide 12 — Persistência: a criptografia

**Eyebrow:** `05 · PERSISTÊNCIA · CRIPTO`

**Título:** Senhas cifradas por uma **passphrase mestra**

**Visual — fluxo vertical (chave nasce da passphrase, vive só na RAM):**

```
   Passphrase mestra (digitada)
              │  deriva (MasterKeyManager)
              ▼
   Chave  ──►  vive SÓ em memória  ──►  cifra/decifra
              │                          senhas dos perfis
              ▼
   Em disco (SQLite): senhas CIFRADAS
   PBE + HMAC-SHA256 + AES-128
```

**Conteúdo:**
- Senhas cifradas com **PBE + HMAC-SHA256 + AES-128**.
- A **chave vive só em memória**, **derivada da passphrase mestra** (`MasterKeyManager`).
  O arquivo em disco, sozinho, **não revela** as senhas.
- Um **verificador** valida a passphrase na abertura.
- ⚠️ Por design: **perder a passphrase = perder o acesso aos segredos** (sem backdoor).

**Nota:** "As senhas nunca ficam em claro no disco. A chave que as decifra é derivada da
passphrase mestra e existe apenas em memória enquanto a app roda. É seguro por construção —
com o custo de que, se você perde a passphrase, perde os segredos."

---

## Slide 13 — O fio condutor: DEV × PROD

**Eyebrow:** `SÍNTESE`

**Título:** O mesmo sistema, dois modos — praticidade × segurança

**Visual — tabela comparativa (coluna DEV azul, coluna PROD coral):**

| Aspecto | DEV (praticidade) | PROD (endurecido) |
|---|---|---|
| noVNC/VNC | **sem senha** | **senha** (`VNC_PASSWORD`) + **TLS** opcional (`CERT_FILE`) |
| Passphrase | **auto-unlock** (`SD_MASTER_PASSPHRASE`) | **digitada** na GUI |
| Criar ambiente | **DooD** (`docker.sock`) | **modo VM** (sem socket) |

**Nota:** "Repare que o trade-off entre facilidade e segurança aparece nos **três** pilares:
no VNC, na passphrase e no provisionamento. É o mesmo código atendendo os dois mundos — só
muda a configuração por variáveis de ambiente."

---

## Slide 14 — Recapitulação e encerramento

**Eyebrow:** `06 · FECHAMENTO`

**Título:** Quatro temas, um sistema coeso

**Conteúdo (recap com ícones das cores semânticas):**
- 🪟 **X Window** — Swing headless via `Xvfb → x11vnc → noVNC`, entregue no navegador.
- 🐳 **Docker** — imagem multi-stage enxuta; a app orquestra containers (DooD) ou VMs.
- 🗄️ **MySQL** — provisionamento automático com volume, readiness e seed.
- 🔒 **Persistência** — dados em volume + perfis cifrados com passphrase mestra.

**Frase de fecho:** *"Cada tema resolve uma parte de um problema real de entrega de
software — e sempre com um trade-off consciente entre praticidade e segurança."*

**Rodapé:** `Obrigado!` · espaço para perguntas · (se sobrar tempo → demo ao vivo)

**Nota:** "Para fechar: os quatro temas não são tópicos soltos, e sim peças de um mesmo
sistema. Obrigado — fico à disposição para perguntas, e posso mostrar o fluxo rodando ao vivo."

---

## Slide 15 (opcional / backup) — Demo & comandos

**Eyebrow:** `APÊNDICE`

**Título:** Se quiser ver rodando

**Visual:** bloco de terminal (mono, fundo Ardósia-painel):

```bash
make build     # compila os módulos + testes
make up        # sobe app (noVNC) + MySQL de teste
               # → abrir http://localhost:6080/vnc.html
make it        # teste de integração: provisiona MySQL real, seed, leitura, limpeza
make down      # derruba a stack
```

**Fluxo na GUI (bullets):** Nova → Testar conexão → Conectar → escrever SQL → **Executar (F5)**.
Provisionar do zero: botão **"Novo ambiente MySQL"**.

**Nota:** slide de reserva para perguntas do tipo "como eu rodaria isso?".

---

# 🛠️ Como montar os diagramas no PowerPoint

Os "desenhos ASCII" acima são só um rascunho — recrie-os com **formas nativas** para
ficarem bonitos:

1. **Caixas:** *Inserir → Formas → Retângulo de cantos arredondados*. Preenchimento
   `#16223A`, borda 1pt `#22304C`. Texto em fonte mono.
2. **Setas do pipeline (Slide 6):** conector *seta* fino, cor `#38A6E0`. Alinhe as caixas
   com *Distribuir horizontalmente* (menu Organizar) para espaçamento perfeito.
3. **Grade de fundo:** crie um retângulo do tamanho do slide com preenchimento em
   *padrão de grade* sutil, ou use uma imagem de grade a ~12% de opacidade, e mande para
   o fundo (*Enviar para trás*). Defina no **Slide Mestre** para aparecer em todos.
4. **Código anotado (Slides 7, 8, 12):** caixa `#16223A`, fonte *Consolas/Cascadia*,
   palavras-chave (`Xvfb`, `x11vnc`, `AES-128`…) coloridas em `#38A6E0`/`#E6A23C`.
5. **Callouts de aviso:** caixinha com borda `#E06A5A` e um ⚠️ — reserve para os
   trade-offs de segurança (DooD, passphrase).
6. **Consistência:** configure a **paleta do tema** (Design → Cores → Personalizar) com
   os 9 hex da tabela; assim todos os elementos puxam as mesmas cores.

> Dica de ritmo: ~1,3 min por slide nos 14 principais = ~18 min, deixando folga para a
> demo e perguntas dentro dos 20 min.
