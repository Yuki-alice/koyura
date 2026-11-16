# Extension platform handoff (`:shared:data`)

## What main agent gets

- `ExtensionSession` (`dev.reader.data.session`): an opened stack exposing
  `service: ExtensionService` (already restored) and `report: RestoreReport`.
  Install/update/call/uninstall and repository management all go through
  `session.service`; `report.registered` / `report.failures` describe the
  restore outcome.
- Platform entry points (same package):
  - Android: `suspend fun Context.openExtensionSession(env: VeneraEnvironment): ExtensionSession`
    (private `filesDir/venera-ext`, `AndroidSqliteDriver`, OkHttp).
  - Desktop JVM: `suspend fun openExtensionSession(appDataDir: Path, env: VeneraEnvironment): ExtensionSession`
    (SQLite file `<appDataDir>/extensions.db`, OkHttp).
  - iOS: `suspend fun openExtensionSession(appDataDir: Path = defaultIosDataDir(), env: VeneraEnvironment): ExtensionSession`
    (SQLiter `extensions.db`, Darwin engine).

## Ownership and lifecycle

- The session owns exactly three resources: `SqlDriver`, `HttpClient`,
  QuickJS runtime (inside `ExtensionService`). `close()` releases all three,
  is idempotent, and must be called once the app no longer needs the stack
  (e.g. app shutdown, account switch, test teardown).
- `openExtensionSession` is idempotent w.r.t. storage: the first open creates
  the schema, later opens reuse repositories, origins and script revisions.
  If open or restore fails (or is cancelled), everything created for that
  attempt is released before the error propagates.
- Reopening means creating a new session on the same directories (file-backed
  drivers only; in-memory drivers do not survive). Double `close()` is safe.

## Minimal wiring (main agent pastes into app startup)

```kotlin
// Android (e.g. Application.onCreate, IO dispatcher + SupervisorJob scope)
val session = applicationContext.openExtensionSession(
    VeneraEnvironment(BuildConfig.VERSION_NAME, localeTag, "android"),
)
// Desktop
val session = openExtensionSession(appDataDir, env)
// iOS
val session = openExtensionSession(environment = env)

// BrowseService adapter reads through session.service:
val installed = session.service.listInstalled()
val detail = session.service.call(key, VeneraCallback.LoadInfo, args)

// Updates:
val report = session.service.checkUpdatesDetailed() // updates + repoFailures + ambiguous/missing
session.service.update(key)

// Shutdown:
session.close()
```

## Commit / recovery contract (for review reference)

- Immutable revision files `<key>@<version>-<hash>.js`; the SQL `extensions`
  row is the commit pointer and moves only after the candidate `init`
  succeeds in the live runtime. Anything observed before that point is a
  complete revision, so killing the process mid-update restarts on the last
  usable revision.
- A failed or cancelled candidate never touches committed state: the DB
  pointer is rolled back first, the orphan revision file is deleted, and the
  exact pre-replace JS instance (tracked by `instanceSeq`, restored via
  `repairDisplaced`) keeps running with its memory state — its `init` runs
  exactly once per revision.
- Persisted name/version always follow the actual script metadata; catalog
  labels are advisory only. Host-control headers (`cache-time`,
  `prevent-parallel`, `http_client`) never reach sites; the default
  `User-Agent` mirrors upstream `webUA`.
