# Fase 4 — Modo VM + empacotamento e distribuição

## Contexto & objetivo
Fechar o ciclo: permitir provisionar/rodar também em **VM** (não só container local) e
**empacotar** a aplicação para distribuição, herdando os artefatos de build do `runner`
(imagem Docker publicável, docker-compose, e opcionalmente RPM/DEB + systemd). Também
endurece o acesso à GUI (noVNC).

## Pré-requisitos
- Fases 0–3 concluídas.

## Escopo
**Inclui:** provisionamento em VM, imagem publicável, compose de produção, endurecimento do
noVNC, e (opcional) pacotes RPM/DEB + serviço systemd.
**Não inclui:** novas funcionalidades de banco (é fase de operação/entrega).

## Estrutura criada / alterada
```
sd-runner/
  modules/provisioner/.../VmMysqlProvisioner.java   # cloud-init / script remoto
  docker/
    Dockerfile              # endurecido (usuário não-root, versões fixas)
    docker-compose.prod.yml # app + volumes + rede; noVNC atrás de auth/TLS
    entrypoint.sh           # x11vnc com senha/token; websockify com --cert (TLS)
  tools/                    # herdados/adaptados do runner
    k8s.yaml                # Deployment (referência runner)
    build_rpm.sh / build_deb.sh / scripts/*  # opcional
  .github/workflows/release.yml   # build + push da imagem (referência runner)
```

## Passos de implementação
1. **`VmMysqlProvisioner`**: mesma interface do `MysqlProvisioner` (Fase 3), mas provisiona
   uma **VM** (ex.: `cloud-init` que instala MySQL, habilita volume de dados e libera a porta);
   a app conecta pela rede e cria o perfil cifrado. Selecionável na UI (container × VM).
2. **Endurecer noVNC**: senha/token no `x11vnc`, `websockify` com TLS (`--cert`), e/ou
   proxy reverso com auth. Parametrizar por env.
3. **Dockerfile de produção**: usuário não-root, versões fixas das libs gráficas, imagem
   final enxuta; espelhar o padrão multi-stage do `runner` (build → runtime).
4. **`docker-compose.prod.yml`**: volumes nomeados (`app-state`, `mysql-data`), rede dedicada,
   `restart: unless-stopped`, e (se DooD for necessário) socket montado só onde imprescindível.
5. **Publicação da imagem**: workflow de CI que builda e publica em registry
   (referência: `.github/workflows/release.yml` e `tools/publish-image.sh` do runner).
6. **(Opcional) RPM/DEB + systemd**: adaptar os profiles `rpm-build`/`deb-build` do
   `modules/app/pom.xml` do runner e os scripts `tools/scripts/*` para instalar a app como
   serviço numa VM/host (útil no modo VM).
7. **k8s (opcional)**: adaptar `tools/k8s.yaml` do runner (Deployment + service para `:6080`).

## Reuso do `runner` (mapa)
| Novo | Origem |
|---|---|
| Profiles RPM/DEB + mappings systemd | `modules/app/pom.xml` (profiles `rpm-build`/`deb-build`) |
| Scripts de serviço (`start.sh`, `.service`, control/*) | `tools/scripts/*` |
| Workflow de release / push de imagem | `.github/workflows/release.yml`, `tools/publish-image.sh` |
| Manifesto k8s | `tools/k8s.yaml` |
| Config por env | `tools/scripts/base_configuration.sh`, `run` |

## Verificação (end-to-end)
1. `docker compose -f docker/docker-compose.prod.yml up` → noVNC exige **senha/TLS**.
2. Provisionar um ambiente em **VM** pela GUI → app conecta e consulta; dados persistem após
   reboot da VM (volume de dados).
3. Imagem publicada é puxável do registry e sobe limpa noutra máquina.
4. **(Se aplicável)** instalar o `.deb`/`.rpm` numa VM → serviço systemd ativo; `journalctl` limpo.
5. **Aceite:** GUI só acessível autenticada; segredos cifrados; persistência confirmada nos
   dois modos (container e VM).

## Riscos & decisões
- **Superfície do noVNC exposto** — nunca publicar sem auth/TLS.
- **DooD em produção**: preferir não montar `docker.sock`; usar modo VM ou DinD isolado.
- Custo/tempo do modo VM (imagem base, cloud-init) — validar provedor-alvo (local libvirt,
  cloud, etc.).
- Licenças das libs gráficas empacotadas na imagem.
