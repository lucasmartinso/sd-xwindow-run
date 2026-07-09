# Fase 3 — Provisionador de MySQL (container com volume + seed)

## Contexto & objetivo
Adicionar o "P" de provisionar: a partir da GUI, **subir um MySQL** com **persistência**
(volume nomeado) e **semear** um schema/dados, e então conectar automaticamente criando um
perfil (Fase 2). Cobre o cenário "conexão via criação de um container docker com uma nova imagem".

## Pré-requisitos
- Fases 0–2 concluídas.
- Acesso ao Docker daemon a partir da app (socket) — ver Riscos.

## Escopo
**Inclui:** módulo `provisioner`, integração com Docker (criar/parar/remover MySQL),
volume persistente, seed configurável, UI de "Novo ambiente".
**Não inclui:** modo VM (Fase 4), empacotamento final (Fase 4).

## Estrutura criada
```
sd-runner/
  modules/provisioner/
    pom.xml
    src/main/java/health/tabia/sdrunner/provisioner/
      MysqlProvisioner.java      # interface: create/start/stop/remove/status
      DockerMysqlProvisioner.java# impl via docker-java (ou ProcessBuilder + docker CLI)
      SeedRunner.java            # aplica .sql de seed usando QueryRunner (core)
      model/EnvironmentSpec.java # nome, versão MySQL, porta, senha, volume, seed path
  docker/seeds/*.sql
```

## Passos de implementação
1. **Módulo `provisioner`** dependendo de `core` (para conectar/semear).
2. **`DockerMysqlProvisioner`**: criar container `mysql:<versão>` com:
   - `MYSQL_ROOT_PASSWORD` (gerado, guardado cifrado no perfil);
   - **volume nomeado** `sdr-<env>-data:/var/lib/mysql` (persistência real entre restarts);
   - porta publicada dinâmica; label `sd-runner=<env>` para gestão.
   - Implementação recomendada: **docker-java**; alternativa simples: `ProcessBuilder`
     chamando o `docker` CLI. (Testcontainers é opção para labs **efêmeros**, mas não dá
     volume persistente por padrão.)
3. **Espera de readiness**: fazer polling com `ConnectionEngine.createDisposableDataSource`
   até o MySQL aceitar conexão (reuso do "testar conexão").
4. **`SeedRunner`**: ler `.sql` de seed e aplicar via `QueryRunner.runQuery` (modo modifying),
   statement a statement.
5. **Persistir como perfil**: ao concluir, criar um `ConnectionProfile` (Fase 2) apontando
   para o ambiente recém-criado, com a senha cifrada.
6. **UI "Novo ambiente"**: form (nome, versão MySQL, seed opcional) → barra de progresso
   (criando → aguardando → semeando → pronto) → conecta automaticamente.
7. **Gestão**: listar/parar/remover ambientes pela label; remover volume é opção explícita
   (para não apagar dados por acidente).

## Reuso do `runner` (mapa)
- **Readiness/seed/execução** → `ConnectionEngine` + `QueryRunner` (Fases 0/1).
- **Padrão de bootstrap de banco vazio** → `Dockerfile` do runner (stage que cria o SQLite).
- **Ideia de datasource descartável para teste** → `createDisposableDataSource`.

## Verificação (end-to-end)
1. Na GUI, "Novo ambiente MySQL" com um seed simples (cria tabela + 3 linhas).
2. **Aceite:** aparece um container com volume `sdr-<env>-data`; a app conecta e lista as 3 linhas.
3. `docker restart` do container → dados persistem (volume).
4. "Remover ambiente" para o container; opção de manter/apagar volume funciona.

## Riscos & decisões
- **Acesso ao Docker (DooD)** montando `/var/run/docker.sock` dá poder amplo sobre o host —
  isolar, restringir por labels e **desabilitar em produção**; considerar rootless/DinD.
- Escolha **docker-java × docker CLI** — decidir aqui (docker-java = mais controle/testes).
- Alocação de portas (evitar conflito) e limpeza de recursos órfãos.
