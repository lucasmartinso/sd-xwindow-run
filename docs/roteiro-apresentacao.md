# Roteiro de Apresentação — sd-runner

> **Tema:** Docker + MySQL + persistência + X Window
> **Duração alvo:** ~20 minutos
> **Formato:** roteiro de fala (sem slides nesta versão)

Cada bloco traz o **tempo sugerido**, os **pontos de fala** e, quando útil, o que **mostrar ao vivo** (demo). O total soma ~20 min com folga para transições.

---

## 0. Abertura e contexto (2 min)

**Fala:**
- Bom dia/boa tarde. Hoje vou apresentar o **sd-runner**, uma aplicação que amarra os quatro temas do trabalho: **Docker**, **MySQL**, **persistência** e **X Window**.
- Em uma frase: é uma **ferramenta de banco de dados** com **interface gráfica** que roda **dentro de um container** e é acessada **pelo navegador**. Ela **cria** ambientes MySQL, **conecta**, **executa queries** e **guarda os perfis de conexão de forma cifrada**.
- O interessante do projeto é que cada tema não aparece isolado — eles se conectam para resolver um problema real: *"como entregar uma app gráfica de banco, sem instalar nada na máquina do usuário, com dados que sobrevivem a reinícios?"*

**Gancho:** durante a apresentação vou mostrar como uma aplicação **Swing** (desktop) acaba rodando **sem monitor**, dentro de um container, e aparecendo numa aba do navegador.

---

## 1. Visão geral da arquitetura (3 min)

**Fala:**
- O projeto é um **Maven multi-módulo** em Java 17. Cada módulo tem uma responsabilidade única, e isso facilita explicar como os quatro temas se encaixam:
  - **`core`** — conexão e execução de query (pool JDBC, streaming de resultados, abort).
  - **`store`** — **persistência** dos perfis com criptografia.
  - **`provisioner`** — cria o **MySQL** (em container Docker ou em VM).
  - **`ui`** — a interface gráfica **Swing** (o "X Window").
  - **`cli`** — um utilitário de linha de comando para smoke-test.
- As dependências fluem de cima para baixo: a `ui` usa `core`, `store` e `provisioner`; todos apoiados no `core`.
- Essa separação é o fio condutor da apresentação: vou passar por **X Window (ui + docker)**, depois **Docker + MySQL (provisioner)** e por fim **persistência (store)**.

**Mostrar (opcional):** a estrutura de pastas no editor, destacando a pasta `modules/` e a pasta `docker/`.

---

## 2. X Window: app gráfica sem monitor, no navegador (5 min)

Este é o bloco mais "diferente" e costuma render boas perguntas — por isso o maior tempo.

**Fala — o problema:**
- A interface é **Swing**, ou seja, uma aplicação **gráfica de desktop**. Normalmente ela precisa de um **servidor gráfico (X)** e de um monitor. Mas queremos rodá-la num **container**, que é headless (sem tela).

**Fala — a solução (a cadeia X Window):**
- A ideia é montar uma pequena **pilha X** dentro do container. A cadeia é:
  1. **Xvfb** — um *X virtual framebuffer*: um servidor X que desenha numa "tela na memória", sem monitor físico. É onde a app Swing acha que está sendo exibida (`DISPLAY=:99`).
  2. **fluxbox** — um gerenciador de janelas leve, para as janelas se comportarem normalmente.
  3. **x11vnc** — captura essa tela virtual e a expõe por **VNC**.
  4. **noVNC + websockify** — traduzem o VNC para **WebSocket**, entregando a tela **no navegador** em `:6080`.
- Resultado: `Swing → Xvfb → x11vnc → noVNC → navegador`. O usuário abre `http://localhost:6080/vnc.html` e usa a app **sem instalar nada**.

**Mostrar — o `entrypoint.sh`:**
- Este script é a sequência de partida do container. Vale mostrar as linhas:
  - `Xvfb :99 -screen 0 1280x800x24` sobe a tela virtual;
  - o loop com `xdpyinfo` **espera a tela ficar pronta** antes de seguir (readiness);
  - `x11vnc` publica a tela; `websockify` faz a ponte web;
  - a última linha, `exec java -jar sd-runner-app.jar`, sobe a aplicação de fato.

**Amarração com segurança (adianta o tema do fim):**
- Repare no `if [ -n "$VNC_PASSWORD" ]`: em **dev** o VNC fica sem senha (praticidade); em **produção** exige senha e pode servir com **TLS** (`CERT_FILE`). Ou seja, o mesmo entrypoint atende os dois modos.

**Demo (se houver ambiente):** `make up` → abrir `http://localhost:6080/vnc.html` e mostrar a GUI aparecendo no navegador.

---

## 3. Docker + MySQL: empacotamento e provisionamento (5 min)

**Fala — o empacotamento (Dockerfile multi-stage):**
- A imagem é **multi-stage**, um padrão importante do Docker:
  - **Stage de build:** usa `maven:3.9-eclipse-temurin-17` para compilar só a `ui` e suas dependências (`-pl modules/ui -am`).
  - **Stage de runtime:** parte de um **JRE** enxuto e instala apenas o necessário para rodar: `xvfb`, `x11vnc`, `fluxbox`, `novnc`, `websockify` e as libs gráficas.
- Vantagem: o Maven e o código-fonte **não vão** para a imagem final — ela fica menor e mais segura. Só o `.jar` compilado é copiado do stage de build.

**Fala — MySQL e provisionamento:**
- O sd-runner não só conecta num MySQL existente; ele consegue **criar um do zero**. É o papel do `provisioner`, em dois modos:
  - **`DockerMysqlProvisioner`** — sobe um **container MySQL** com **volume nomeado** (persistência dos dados), **espera o banco ficar pronto** (readiness) e aplica um **seed** inicial.
  - **`VmMysqlProvisioner`** — para o modo VM: gera um arquivo **`cloud-init`** e delega o boot da máquina a um launcher configurável (ex.: `multipass`).

**Fala — DooD (Docker-out-of-Docker) e o trade-off:**
- Para o botão "Novo ambiente MySQL" funcionar, o container precisa **falar com o Docker do host**. Fazemos isso montando o `docker.sock` (DooD) — o container usa o Docker CLI para criar um container **irmão** de MySQL.
- **Trade-off honesto:** montar o `docker.sock` dá acesso amplo ao host. Por isso ele só é montado em **dev**. Em **produção** isso é desabilitado, e o caminho recomendado é o **modo VM**.

**Mostrar:** o `Dockerfile` (destacar os dois `FROM`) e a tabela fase→entrega do README, ou os dois `docker-compose` (dev vs. prod).

**Demo (opcional):** `make it` roda o teste de integração que provisiona um MySQL real, aplica o seed, lê linhas e limpa ao final.

---

## 4. Persistência: perfis cifrados com passphrase mestra (3 min)

Há **dois níveis** de persistência no projeto — vale distinguir os dois.

**Fala — persistência dos dados do banco:**
- Já vista no bloco anterior: o container MySQL usa **volume nomeado**, então os dados **sobrevivem** a reinícios do container.

**Fala — persistência dos perfis de conexão (o `store`):**
- Os perfis (host, porta, usuário, senha) ficam num **SQLite local**.
- As **senhas são cifradas em repouso** (PBE + HMAC-SHA256 + AES-128).
- Detalhe importante de segurança: a **chave vive só em memória**, **derivada de uma passphrase mestra** (`MasterKeyManager`). Ou seja, o arquivo no disco sozinho não revela as senhas.
- Um "verificador" valida a passphrase na abertura. Consequência de design: **perder a passphrase = perder o acesso aos segredos** (não há backdoor).

**Amarração:** é aqui que dev e prod divergem de novo — em dev, a variável `SD_MASTER_PASSPHRASE` **auto-desbloqueia** o store (para demo/headless); em prod, o usuário **digita** a passphrase na própria GUI.

---

## 5. Fechamento (2 min)

**Fala — recapitulação (amarrando os 4 temas):**
- **X Window:** uma app Swing roda headless via `Xvfb → x11vnc → noVNC`, entregue no navegador.
- **Docker:** imagem multi-stage enxuta; a própria app orquestra containers (DooD) ou VMs.
- **MySQL:** provisionamento automático com volume, readiness e seed.
- **Persistência:** dados em volume + perfis cifrados com passphrase mestra.

**Fala — o que eu tiraria de aprendizado:**
- Cada tema resolve uma parte de um problema real de entrega de software: rodar uma GUI sem instalar nada, num ambiente reprodutível, com dados e segredos preservados.
- E há sempre um **trade-off entre praticidade (dev) e segurança (prod)** — visível no VNC (senha/TLS), no DooD (host vs. VM) e no auto-unlock da passphrase.

**Encerramento:** obrigado — aberto a perguntas. (Se sobrar tempo: demo ao vivo do fluxo completo.)

---

## Apêndice — colinha de comandos para a demo

```bash
make build        # compila os módulos + testes
make up           # sobe app (noVNC) + MySQL de teste  → http://localhost:6080/vnc.html
make it           # teste de integração: provisiona MySQL real, seed, leitura, limpeza
make down         # derruba a stack
VNC_PASSWORD='...' make up-prod   # modo endurecido (senha no VNC, sem DooD)
```

**Fluxo na GUI:** Nova → Testar conexão → Conectar → SQL → Executar (F5). Provisionar do zero: **Novo ambiente MySQL**.

---

## Guia de tempo (resumo)

| Bloco | Tema | Tempo |
|------|------|-------|
| 0 | Abertura e contexto | 2 min |
| 1 | Visão geral da arquitetura | 3 min |
| 2 | X Window (ui + docker) | 5 min |
| 3 | Docker + MySQL (provisioner) | 5 min |
| 4 | Persistência (store) | 3 min |
| 5 | Fechamento | 2 min |
| — | **Total** | **~20 min** |
</content>
</invoke>
