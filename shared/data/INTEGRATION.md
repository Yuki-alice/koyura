# Extension platform handoff (`:shared:data`)

## Session API

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

## Platform wiring

The default `App()` already owns its session and uses these factories. Code
that owns a session itself can use the same API.

```kotlin
// Android (e.g. Application.onCreate, IO dispatcher + SupervisorJob scope)
val session = applicationContext.openExtensionSession(
    VeneraEnvironment("1.16.0", localeTag, "android"),
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

## Image callbacks (`onResponse` / `onLoadFailed`, full + thumbnail)

- Runtime (`VeneraSourceRuntime`, `:shared:source-runtime`): `parseImageConfig`
  (JSON-safe part + integer handles into a JS-side registry),
  `invokeImageOnResponse(handle, bytes): ByteArray` (sync/async, normalizes
  Uint8Array views/ArrayBuffers with correct offsets, rejects the rest),
  `invokeImageOnLoadFailed(handle)` (new config or null when declined),
  `releaseImageCallbacks(handles)` (best-effort, idempotent),
  `imageCallbackCount()` (diagnostics; must return to 0 after each load).
  Function values never cross the JSON boundary; the registry holds them by
  identity, so releasing one config can never free another's callbacks.
- Service (`ComicReadingService.loadImageBytes`, `loadThumbnailBytes`):
  full-size images retry through `onLoadFailed` replacements up to 5
  additional fetches (4xx/5xx, transport failures and invalid transforms are
  retryable; cancellations, closed runtime, invalid configs and
  `modifyImage` are not). Thumbnails do a single attempt and ignore
  `onLoadFailed`. Image downloads never hold the runtime lock, so other
  source callbacks proceed while bytes stream in.
- Explicitly deferred (later stage): `modifyImage` scripts (need an isolated
  engine), disk image cache, app-layer cover wiring (A owns `shared/app`).
