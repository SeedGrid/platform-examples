# seedgrid-backup-example

App de exemplo, rodável, do `seedgrid-quarkus-ext-backup`: export assíncrono de dados de uma
aplicação em ZIP (CSV por tabela + `manifest.json` + `checksums.txt`), com job/status/download via
REST, protegido por RBAC (`seedgrid-security`).

Domínio de exemplo: `Note` (anotações pessoais do usuário logado) — `POST/GET /api/notes` — usado
como "dado real" pra alimentar o export.

## Nota histórica: 3 bugs da extensão corrigidos na `2026.07.08`

Ao validar o ciclo **assíncrono completo** (`POST /exports` → execução em background → `DONE` →
download do ZIP **gerado de verdade**) contra a versão `2026.07.02`, este exemplo revelou 3 bugs
reais no `seedgrid-quarkus-ext-backup` — todos **corrigidos na `2026.07.08`** (que este exemplo
usa agora):

1. **Schema errado em host standalone** — o `SingleTenantBackupProvider` (`@DefaultBean`)
   devolvia o literal `"default"` como schema, então o extrator RAW (`WHERE table_schema =
   :schema`) sempre encontrava 0 tabelas. *Corrigido*: devolve `"public"` (e `getAllTenants()`
   retorna `["public"]`, então os schedulers também funcionam em standalone). O workaround
   `PublicSchemaTenantProvider` (`@Alternative`) que existia neste exemplo foi removido.
2. **Deadlock do worker assíncrono** — `executeExportAsync` em `Thread.ofVirtual()` crua +
   `QuarkusTransaction.requiringNew()` envolvendo chamadas `@Transactional(REQUIRES_NEW)` de
   `updateProgress` travava indefinidamente. *Corrigido*: thread de plataforma, sem transação
   longa envolvente, request context e progresso atualizados programaticamente.
3. **Entidade destacada no final do pipeline** — a suspensão/retomada de transação deixava o
   `job` destacado da sessão e `saveArtifacts → job.addArtifact(...)` falhava. *Corrigido*:
   snapshot imutável durante o pipeline + reload do job na transação final. Mudança observável:
   `ExportContext.exportJob` não é mais populado pelo pipeline.

Com isso, o `NoteExportFlowTest` deste exemplo passou a exercitar o **pipeline assíncrono real**
de ponta a ponta (antes ele simulava a conclusão anexando um artefato manualmente via
repository). A própria extensão também cobre esse ciclo no `ExportAsyncFlowIT`.

## Fluxo demonstrado

1. `POST /public/auth/login` — humano loga (admin do bootstrap do `seedgrid-security`).
2. `POST /api/notes` — cria uma anotação (dado real).
3. `POST /exports` `{"format":"CSV_ZIP","scope":"RAW"}` — cria o job de export (endpoint pronto da
   extension), retorna `201 QUEUED`.
4. `GET /exports/{jobId}` — consulta status.
5. `GET /exports` — lista os jobs do usuário atual (paginação opcional `?page=&size=`).
6. `GET /exports/{jobId}/download` — baixa o ZIP quando `DONE`.

## Como rodar

```bash
./run-dev.sh          # modo dev, carrega dev.env
mvn test              # sobe Postgres via Testcontainers automaticamente
```

## Testes

- `BackupSchemaBootstrapTest` — migrations de security + backup + app (notes) rodam no mesmo
  banco, numa única tabela de histórico do Flyway; permissions do domínio (`NOTE_READ`/
  `NOTE_CREATE`) e do backup (`BACKUP_CREATE`/`BACKUP_READ`/`BACKUP_DOWNLOAD`) semeadas e
  vinculadas à role `ADMIN`.
- `NoteExportFlowTest` — fluxo assíncrono REAL de ponta a ponta
  (`seedgrid.backup.async-enabled=true`): cria nota + job via HTTP, faz polling até `DONE`,
  valida `progress=100`, `databaseSchema=public` (provider default, sem override), artefato ZIP e
  download com bytes reais (assinatura `PK`); lista jobs do usuário atual; confirma 404 pra job
  inexistente.

## Configuração

`quarkus.flyway.locations`/`quarkus.flyway.table` precisam ser explícitos: tanto
`seedgrid-backup` quanto `seedgrid-audit-postgresql` empacotam seu próprio
`META-INF/microprofile-config.properties` fixando esses valores como default — sem sobrescrever,
só as migrations do último módulo configurado seriam aplicadas.
