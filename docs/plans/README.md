# sd-runner — Planos de implementação

Aplicação **standalone** de banco de dados: painel gráfico (GUI via **noVNC**) que
**provisiona** um MySQL (container ou VM), **conecta** e **consulta**, com perfis
persistidos e cifrados. Reaproveita os "tijolos" do projeto `runner`
(conexão JDBC + pool, execução de query com streaming/abort, criptografia,
padrões de empacotamento Docker), trocando o "cérebro remoto" (CareOS/STOMP) por
uma UI local.

> Tema-base: **Docker + MySQL + persistência + X Window**.

## Decisões transversais (valem para todas as fases)

- **Linguagem/build:** Java 17 + Maven multi-módulo (espelha `runner`).
- **Runtime:** Spring Boot em modo não-web (`WebApplicationType.NONE`) só para DI;
  UI em **Swing** (mais leve para noVNC no POC).
- **Banco alvo:** MySQL (`mysql-connector-j`, scope `compile` — diferente do
  `runner`, onde é `provided`).
- **Estado da app:** SQLite + Liquibase em volume (padrão herdado do `runner`).
- **Criptografia:** `Encryptor` do `runner` com **nova fonte de chave**
  (passphrase mestra derivada por PBKDF2, só em memória).
- **GUI/X Window:** `Xvfb + x11vnc + noVNC` no container (acesso via navegador `:6080`).
- **Pacote/grupo Maven:** `health.tabia.sdrunner`, artefato raiz `sd-runner-parent`.

## Fases

| Fase | Plano | Entrega |
|---|---|---|
| 0 | [plan-fase-0.md](plan-fase-0.md) | Repo multi-módulo + `core` (datasource/query) rodando query MySQL via CLI |
| 1 | [plan-fase-1.md](plan-fase-1.md) | GUI mínima (conexões + editor SQL + resultados) via noVNC no container |
| 2 | [plan-fase-2.md](plan-fase-2.md) | Persistência cifrada de perfis (SQLite + Encryptor + passphrase) |
| 3 | [plan-fase-3.md](plan-fase-3.md) | Provisionador: sobe MySQL container com volume + seed |
| 4 | [plan-fase-4.md](plan-fase-4.md) | Modo VM + empacotamento (Dockerfile/compose/RPM/DEB) |

Cada fase é incremental e verificável de ponta a ponta antes de seguir.
