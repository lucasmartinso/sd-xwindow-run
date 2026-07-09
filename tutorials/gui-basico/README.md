# Tutorial — GUI (básico)

Objetivo: subir o sd-runner em **modo DEV** e, pela interface no navegador (noVNC),
criar um perfil de conexão, testar a conexão e executar um SQL. É o caminho mais
rápido para "ver a tela funcionando".

No modo DEV a aplicação **auto-desbloqueia** o store (via `SD_MASTER_PASSPHRASE`) e o
noVNC fica **sem senha** — ideal para testar, mas não use assim em produção
(ver [gui-avancado](../gui-avancado/README.md)).

---

## Pré-requisitos

- Docker + Docker Compose v2 rodando
- Projeto compilado ao menos uma vez (`make build` na raiz)
- Um navegador

---

## Passo 1 — Subir a stack de DEV

Na raiz do projeto:

```bash
cd ~/projects/sd-runner
make up
```

Isso sobe dois containers (ver `docker/docker-compose.yml`):

- **sd-studio** — a aplicação Swing servida via noVNC em `:6080`
- **mysql** — um MySQL 8 de teste, com database `sdr_demo`, root/senha `dev`,
  publicado na porta `3307` do host e `3306` dentro da rede do compose

O container da app enxerga o MySQL pelo hostname **`mysql`** na porta **`3306`**.

> Primeira execução faz o build da imagem (`--build`), então pode demorar alguns
> minutos.

## Passo 2 — Abrir a GUI no navegador

Acesse:

```
http://localhost:6080/vnc.html
```

Clique em **Connect**. Como estamos em DEV, **não** há senha de VNC. Você verá a
janela **"sd-runner — DB Studio"** com três áreas:

- **Conexões** (à esquerda): lista de perfis + botões
- **SQL** (centro/topo): editor de query com um `SELECT 1 AS ok;` de exemplo
- **Resultados** (centro/baixo): tabela de resultados
- Barra de **status** no rodapé (começa com "Pronto")

## Passo 3 — Criar um perfil de conexão

No painel **Conexões**, clique em **Nova**. Preencha o formulário apontando para o
MySQL da stack:

| Campo    | Valor   |
|----------|---------|
| Nome     | `demo`  |
| Host     | `mysql` |
| Porta    | `3306`  |
| Database | `sdr_demo` |
| Usuário  | `root`  |
| Senha    | `dev`   |

> Use `mysql:3306` (rede interna do compose), **não** `localhost:3307` — esta última
> é a porta publicada para acesso a partir da sua máquina, fora do container.

## Passo 4 — Testar a conexão

Ainda no formulário, clique em **Testar conexão**. A app abre um datasource
descartável e valida a conexão em até 5s:

- Sucesso → caixa de diálogo **"Conexão OK"**
- Falha → **"Falha: ..."** com a mensagem do driver (revise host/porta/credenciais)

Se estiver OK, clique em **Salvar**. O perfil aparece na lista de **Conexões**
(fica persistido no store cifrado).

## Passo 5 — Conectar

Selecione o perfil `demo` na lista e clique em **Conectar**. A barra de status deve
mostrar **"Conectado: demo"**. Isso registra o pool de conexões para esse perfil.

## Passo 6 — Executar um SQL

No editor **SQL**, deixe o exemplo ou digite uma query, por exemplo:

```sql
SELECT NOW() AS agora, VERSION() AS versao;
```

Clique em **Executar (F5)** (ou aperte **F5** com o cursor no editor). Os resultados
aparecem na tabela **Resultados** e o status mostra **"OK — N linha(s)"**.

- Queries que começam com `SELECT`, `SHOW`, `DESCRIBE` ou `EXPLAIN` são tratadas
  como **leitura**.
- Qualquer outra coisa (ex.: `CREATE`, `INSERT`, `UPDATE`) é tratada como
  **modificação** automaticamente.
- Para uma query longa, use **Abortar** para cancelar a execução em andamento.

## Passo 7 — Derrubar a stack

Ao terminar:

```bash
make down
```

Os volumes `app-state` (perfis) e `mysql-data` (dados) **permanecem** entre subidas,
então seus perfis e dados de teste continuam lá no próximo `make up`.

---

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---------|----------------|-------------|
| Navegador não abre em `:6080` | Container ainda subindo/build em curso | `docker compose -f docker/docker-compose.yml logs -f sd-studio` |
| "Falha: Communications link failure" | Host/porta errados | Use `mysql` / `3306` (rede interna), não `localhost/3307` |
| Tela cinza no noVNC | X ainda inicializando | Aguarde e recarregue; a app sobe após Xvfb → x11vnc → noVNC |
| Perfil sumiu | Volume removido | Não use `down -v`; os perfis vivem no volume `app-state` |

## Próximos passos

- Testar **modo PROD** (senha VNC, passphrase manual, TLS) e o botão
  **Novo ambiente MySQL** → [gui-avancado](../gui-avancado/README.md)
- Testar sem abrir tela → [cli-basico](../cli-basico/README.md)
