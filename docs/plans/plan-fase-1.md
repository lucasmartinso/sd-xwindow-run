# Fase 1 — GUI mínima rodando via noVNC no container

## Contexto & objetivo
Colocar uma **interface gráfica (Swing)** sobre o `core` da Fase 0 e torná-la acessível
pelo **navegador via noVNC**, dentro de um container Docker. Ao final, o usuário registra
uma conexão MySQL em runtime (na memória, ainda sem persistir), roda SQL e vê os resultados.

## Pré-requisitos
- Fase 0 concluída (`core` + CLI funcionando).
- Docker instalado.

## Escopo
**Inclui:** módulo `ui` (Swing), imagem Docker com Xvfb/x11vnc/noVNC, docker-compose de dev.
**Não inclui:** persistência cifrada (Fase 2), provisionamento automático (Fase 3).

## Estrutura criada
```
sd-runner/
  modules/ui/
    pom.xml
    src/main/java/health/tabia/sdrunner/ui/
      App.java              # Spring Boot (WebApplicationType.NONE) + bootstrap Swing
      MainFrame.java        # layout: lista de conexões | editor SQL | tabela de resultados
      ConnectionDialog.java # form: host/porta/user/senha/driver (+ "Testar")
      QueryPanel.java       # RSyntaxTextArea (ou JTextArea) + botão Executar/Abortar
      ResultTableModel.java # consome callbacks do QueryRunner (streaming -> tabela)
  docker/
    Dockerfile
    entrypoint.sh
    docker-compose.yml
```

## Passos de implementação
1. **Módulo `ui`** dependendo de `core`. UI em **Swing** (POC). Opcional: `rsyntaxtextarea`
   para realce de SQL.
2. **`App.java`**: subir contexto Spring não-web (como `Application.java` do runner) e, no
   `PostConstruct`/`CommandLineRunner`, iniciar o Swing na EDT (`SwingUtilities.invokeLater`).
3. **`MainFrame`**: `JSplitPane` — esquerda lista de conexões (em memória), centro editor,
   baixo tabela de resultados.
4. **`ConnectionDialog`**: coleta dados e chama `ConnectionEngine.createDisposableDataSource`
   para o botão **"Testar conexão"** (reuso direto do runner).
5. **`QueryPanel` + `ResultTableModel`**: ao executar, chamar `QueryRunner.runQuery(...)` numa
   thread de trabalho; o callback de cabeçalho define colunas; cada linha é adicionada à tabela;
   a sentinela de fim libera a UI; botão **Abortar** chama `RunningQueries.abort(execId)`.
6. **Dockerfile (multi-stage, herdando o padrão do runner)**:
   - stage build: `mvnw package`;
   - stage runtime: JRE 17 + `xvfb x11vnc fluxbox novnc websockify libgtk-3-0 fonts-dejavu`;
   - `EXPOSE 6080`; `CMD entrypoint.sh`.
7. **`entrypoint.sh`**: `Xvfb :99` → `export DISPLAY=:99` → `fluxbox &` → `x11vnc -display :99
   -forever -nopw -bg` → `websockify --web=/usr/share/novnc 6080 localhost:5900 &` →
   `exec java -jar /app.jar`.
8. **docker-compose.yml (dev)**: serviço `sd-studio` (build `./docker`, `ports: 6080:6080`) +
   serviço `mysql:8` com volume `mysql-data` para já ter um alvo de teste.

## Reuso do `runner` (mapa)
- Padrão de bootstrap Spring não-web → `Application.java`.
- Dockerfile multi-stage → `Dockerfile` do runner.
- "Testar conexão" e execução → `ConnectionEngine` + `QueryRunner` da Fase 0.

## Verificação (end-to-end)
1. `docker compose up --build`.
2. Abrir `http://localhost:6080` no navegador → aparece o desktop com a app Swing.
3. Registrar conexão para o serviço `mysql` (host `mysql`, porta 3306, root/dev), clicar
   **Testar** (ok verde), rodar `SHOW DATABASES;` → tabela populada via streaming.
4. **Aceite:** query longa faz streaming incremental; **Abortar** interrompe; nenhuma
   credencial aparece em log.

## Riscos & decisões
- **Swing × JavaFX**: manter Swing no POC; reavaliar se precisar de UI mais rica.
- Fonte/DPI no Xvfb (definir resolução em `entrypoint.sh`).
- Segurança do noVNC (POC sem senha; endurecer na Fase 4 — TLS/senha/token).
