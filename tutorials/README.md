# Tutoriais do sd-runner

Guias passo a passo para **testar** o sd-runner. Estão divididos por **interface**
(GUI no navegador via noVNC, ou CLI de linha de comando) e por **nível**
(configurações básicas vs. avançadas).

| Tutorial | Interface | Nível | O que cobre |
|----------|-----------|-------|-------------|
| [gui-basico](./gui-basico/README.md) | GUI (noVNC) | Básico | Subir a stack de DEV, criar perfil, testar conexão, rodar SQL |
| [cli-basico](./cli-basico/README.md) | CLI | Básico | Compilar o uber-jar da CLI e rodar um SQL de smoke-test |
| [gui-avancado](./gui-avancado/README.md) | GUI (noVNC) | Avançado | Modo PROD (senha VNC + TLS), passphrase mestra, "Novo ambiente MySQL", persistência |
| [cli-avancado](./cli-avancado/README.md) | CLI | Avançado | Flags `--modifying`/`--limit`/`--driver`, provisionamento (Docker/VM), teste de integração, imagem |

## Antes de começar

Pré-requisitos comuns a todos os tutoriais (ver `../README.md` §2):

- **JDK 17** e **Maven 3.6+**
- **Docker** e **Docker Compose v2**
- Um navegador (para a GUI via noVNC)

Compile tudo uma vez a partir da raiz do projeto:

```bash
cd ~/projects/sd-runner
make build      # compila todos os módulos + testes unitários
```

## Qual tutorial seguir?

- **Só quero ver a tela funcionando** → [gui-basico](./gui-basico/README.md)
- **Quero validar uma conexão/SQL sem abrir tela** → [cli-basico](./cli-basico/README.md)
- **Quero testar segurança/persistência/provisionamento pela tela** → [gui-avancado](./gui-avancado/README.md)
- **Quero automatizar, provisionar ou empacotar via terminal** → [cli-avancado](./cli-avancado/README.md)
