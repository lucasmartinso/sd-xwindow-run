# Fase 2 — Persistência cifrada de perfis (SQLite + Encryptor + passphrase)

## Contexto & objetivo
Tornar as conexões **persistentes entre execuções** e **cifradas em repouso**, reaproveitando
o esquema de criptografia do `runner` — mas com uma **nova origem de chave** (a app é
standalone, não há CareOS para enviar a chave). O usuário define uma **passphrase mestra**;
dela deriva-se a chave que cifra as senhas dos perfis.

## Pré-requisitos
- Fase 1 concluída (GUI + `core`).

## Escopo
**Inclui:** módulo `store` (SQLite + Liquibase), `Encryptor` com chave por passphrase,
tela de desbloqueio (master password), CRUD de perfis de conexão e histórico de queries.
**Não inclui:** provisionamento (Fase 3), empacotamento/VM (Fase 4).

## Estrutura criada
```
sd-runner/
  modules/store/
    pom.xml
    src/main/resources/db/changelog/   # Liquibase: 0-init, 1-profiles, 2-history
    src/main/java/health/tabia/sdrunner/store/
      LocalDatasource.java     # port de jpa/LocalDatasourceConfiguration (SQLite)
      Encryptor.java           # port de utils/Encryptor
      AttributeEncryptor.java  # port (JPA AttributeConverter)
      MasterKeyManager.java    # NOVO: substitui DataSecurityManager (chave em memória)
      model/ConnectionProfile.java   # host/porta/user/senha(cifrada)/driver/opts
      model/QueryHistory.java
      ProfileRepository.java / HistoryRepository.java
```

## Passos de implementação
1. **Módulo `store`** com `spring-boot-starter-data-jpa`, `sqlite-jdbc`,
   `hibernate-community-dialects` (dialeto SQLite), `liquibase-core` — mesmas libs do runner.
2. **`LocalDatasource`**: SQLite em `${STATE_DIR}/app.db` (ex.: `/opt/sd-runner/state`),
   `?foreign_keys=on` — port de `LocalDatasourceConfiguration.java`.
3. **`MasterKeyManager` (NOVO)**: recebe a passphrase da UI, deriva a chave
   (PBKDF2/scrypt) e a mantém **só em memória**; expõe o mesmo contrato que o `Encryptor`
   espera (o `encryptionKeySupplier`). Substitui o `DataSecurityManager` do runner
   (que recebia a chave do servidor).
4. **Portar `Encryptor` + `AttributeEncryptor`**: idênticos ao runner (PBE+HMAC-SHA256+AES-128);
   só muda quem alimenta a chave (agora o `MasterKeyManager`).
5. **`ConnectionProfile`**: campo `password` com `@Convert(AttributeEncryptor.class)`
   (mesma técnica do `Connector` do runner). `QueryHistory` guarda SQL + timestamp
   (opcionalmente cifrado).
6. **Liquibase changelog**: `profiles` e `history`; validação de aplicação na subida.
7. **UI**: tela de **desbloqueio** (pedir passphrase no start) → inicializa `MasterKeyManager`;
   lista de conexões passa a vir do `ProfileRepository`; salvar/editar/excluir perfis;
   painel de histórico reexecutável.
8. **Primeira execução**: se não há `app.db`, criar e pedir para **definir** a passphrase
   (guardar um verificador — ex.: hash de um token conhecido — para validar tentativas futuras).

## Reuso do `runner` (mapa)
| Novo | Origem |
|---|---|
| `LocalDatasource` | `.../jpa/LocalDatasourceConfiguration.java` |
| `Encryptor` / `AttributeEncryptor` | `.../utils/Encryptor.java`, `.../utils/AttributeEncryptor.java` |
| `ConnectionProfile` (padrão @Convert) | `.../models/Connector.java` |
| Liquibase changelog | `.../resources/db/changelog/*` |
| **`MasterKeyManager`** (redesenho) | substitui `.../services/DataSecurityManager.java` |

## Verificação (end-to-end)
1. Start → pedir passphrase; criar um perfil MySQL e salvar.
2. Inspecionar `app.db` (ex.: `sqlite3 app.db "select password from connection_profile"`) →
   valor **cifrado** com prefixo do `Encryptor`.
3. Reiniciar o container → perfis reaparecem; abrir com a passphrase correta decifra;
   passphrase errada **não** decifra.
4. **Aceite:** senhas nunca em claro no disco; histórico persiste; reexecução funciona.

## Riscos & decisões
- **Perda da passphrase = perda dos segredos** (por design). Documentar; opcional: export/import
  de perfis com re-cifragem.
- Definir KDF e parâmetros (iterações) — decidir aqui.
- Onde guardar o verificador da passphrase sem enfraquecer a segurança.
