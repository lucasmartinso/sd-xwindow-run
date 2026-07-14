# Tutorial — GUI (avançado)

Objetivo: testar pela interface os recursos que o [gui-basico](../gui-basico/README.md)
não cobre — **modo PROD endurecido** (senha no noVNC, TLS opcional), **passphrase
mestra** digitada manualmente, provisionamento pelo botão **"Novo ambiente MySQL"**,
histórico e persistência dos perfis.

Recomenda-se ter feito o tutorial básico antes.

---

## 1. Modo PROD: subir com senha no noVNC

O modo PROD (`docker/docker-compose.prod.yml`) endurece a stack:

- exige **senha no noVNC** (`VNC_PASSWORD`, obrigatória)
- **não** faz auto-unlock — você digita a passphrase mestra na GUI
- **não** monta o `docker.sock` (sem DooD; provisionamento por container fica indisponível)

```bash
cd ~/projects/sd-runner
VNC_PASSWORD='troque-esta-senha' make up-prod
```

Acesse `http://localhost:6080/vnc.html`, clique em **Connect** e informe a **senha do
VNC** definida acima. Se `VNC_PASSWORD` não for definida, o compose falha
propositalmente (`?set VNC_PASSWORD`).

Derrubar a stack de PROD:

```bash
docker compose -f docker/docker-compose.prod.yml down
```

## 2. Definir e usar a passphrase mestra

Sem auto-unlock, ao abrir a app aparece o diálogo de desbloqueio:

- **Primeira execução** (store novo) → **"Defina a passphrase mestra"** com campo de
  **confirmação**. A passphrase não pode ser vazia e as duas precisam conferir.
- **Execuções seguintes** → **"Passphrase mestra"**. Se errar, aparece **"Passphrase
  incorreta."** e a app encerra.

> A chave de criptografia vive **só em memória**, derivada da passphrase (PBE +
> HMAC-SHA256 + AES-128). **Perder a passphrase = perder os segredos**, por design.
> Não há recuperação.

Para testar do zero (store limpo) você pode remover o volume de estado antes de subir:

```bash
docker compose -f docker/docker-compose.prod.yml down -v   # apaga app-state (perfis!)
```

## 3. TLS no noVNC (opcional)

Para servir o noVNC sobre **wss/TLS**, monte um certificado no container e aponte
`CERT_FILE` para ele. No `docker-compose.prod.yml` há linhas comentadas prontas:

```yaml
environment:
  CERT_FILE: /certs/novnc.pem
volumes:
  - ./certs/novnc.pem:/certs/novnc.pem:ro
```

Gere um certificado de teste (PEM com chave + cert concatenados):

```bash
mkdir -p docker/certs
openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 30 -subj '/CN=localhost'
cat key.pem cert.pem > docker/certs/novnc.pem && rm key.pem cert.pem
```

Descomente as linhas, suba com `make up-prod` e acesse via **`https://localhost:6080/vnc.html`**
(o navegador vai avisar sobre o certificado autoassinado — aceite para testar).

## 4. Provisionar um banco pelo botão "Novo ambiente MySQL"

> Requer **DooD** (o `docker.sock` montado), que existe **apenas na stack de DEV**
> (`make up`). Na stack de PROD isso está desabilitado por segurança — use o modo VM
> (ver [cli-avancado](../cli-avancado/README.md#modo-vm)).

Na stack de DEV, no painel **Conexões**, clique em **Novo ambiente MySQL** e preencha:

| Campo | Padrão | Observação |
|-------|--------|------------|
| Nome | `dev` | vira nome do container (`sdr-env-<nome>`) e do volume (`sdr-<nome>-data`) — use um nome **ainda não usado** (ver aviso abaixo) |
| Versão MySQL | `8` | tag da imagem `mysql` |
| Database | `app` | database inicial criado |
| Senha root | `dev` | senha do root do novo MySQL |
| Seed (SQL) | script de exemplo | statements separados por `;`, aplicados após readiness |

Clique em **Provisionar**. A app (em background):

1. cria o container MySQL com **volume nomeado** (persistência),
2. espera o banco ficar pronto,
3. aplica o **seed**,
4. cria e salva um **perfil de conexão** apontando para o ambiente,
5. atualiza o status com **"Ambiente pronto: ... (porta N)"**.

O novo perfil aparece na lista. Selecione, **Conectar** e rode um `SELECT * FROM users`
para conferir o seed.

> **Como a app alcança o banco provisionado.** A app roda **dentro** de um container e
> cria o MySQL pelo daemon do host (DooD). Uma porta publicada no host **não** é
> acessível por `127.0.0.1` de dentro do container. Por isso a stack de DEV define
> `SD_PROVISION_NETWORK: sdr-dev-net` (ver `docker-compose.yml`): o container novo é
> anexado a essa rede e a app fala com ele pelo **nome** (`sdr-env-<nome>:3306`) via DNS
> do Docker — o perfil salvo já nasce com esse endereço. A porta continua publicada no
> host também, então você pode acessar o mesmo banco pela sua máquina se quiser.
>
> **Nome já em uso.** O nome vira o do container (`sdr-env-<nome>`). Se já existir um
> container com esse nome (inclusive **parado**, de um teste anterior), o provisionamento
> falha com *"Falha ao provisionar: docker run failed: ... Conflict. The container name
> ... is already in use"*. Use um nome novo, ou remova o antigo:
> `docker rm -f sdr-env-<nome> && docker volume rm sdr-<nome>-data`.

## 5. Histórico e persistência

- Toda query executada é registrada no **histórico** do perfil (armazenado no store,
  com timestamp).
- Os perfis (com senha **cifrada em repouso**) ficam no `app.db` dentro de
  `STATE_DIR` (no container, `/app/state`, mapeado no volume `app-state`).
- Os botões **Editar** e **Excluir** atualizam o store; **Excluir** também remove o
  pool de conexões ativo daquele perfil.

## 6. Ajustar a resolução da tela (GEOMETRY)

A resolução do display virtual (Xvfb) é controlada por `GEOMETRY` (padrão
`1280x800x24`). Para uma tela maior, edite o `environment` no compose:

```yaml
environment:
  GEOMETRY: 1600x900x24
```

Recrie o container (`make up`) e recarregue o noVNC.

---

## Referência rápida de variáveis (surfaces desta GUI)

| Variável | Efeito |
|----------|--------|
| `SD_MASTER_PASSPHRASE` | Auto-desbloqueia o store (só DEV/demo). Em PROD, **não** definir. |
| `SD_PROVISION_NETWORK` | Rede Docker à qual anexar os ambientes provisionados; a app os alcança pelo nome do container na porta `3306`. Sem ela (ex.: CLI no host), usa `127.0.0.1` + porta publicada. |
| `VNC_PASSWORD` | Habilita senha no x11vnc/noVNC (obrigatória no compose de PROD). |
| `CERT_FILE` | Se definido, o noVNC serve sobre TLS (wss). |
| `GEOMETRY` | Resolução do Xvfb (padrão `1280x800x24`). |
| `STATE_DIR` | Pasta do `app.db` (no container, `/app/state`). |

## Avisos de segurança

- **Nunca** exponha o noVNC sem senha fora de DEV local.
- **DooD** (montar `docker.sock`) dá acesso amplo ao host — use só em DEV.
- Em PROD, prefira senha VNC + TLS + modo VM para provisionamento.

## Próximos passos

- Provisionamento e empacotamento via terminal → [cli-avancado](../cli-avancado/README.md)
