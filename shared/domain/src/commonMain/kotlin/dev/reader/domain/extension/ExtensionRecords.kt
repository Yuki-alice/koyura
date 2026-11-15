package dev.reader.domain.extension

/**
 * Milestone 2/3 domain: persistence-agnostic extension records.
 *
 * SQLDelight owns rows, Okio owns script bytes; [ExtensionService] in :shared:data
 * orchestrates QuickJS validation, atomic file+row commits and restart recovery.
 */
data class RepositoryRecord(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)

data class OriginRecord(
    val sourceKey: String,
    val kind: String,
    val repositoryId: String? = null,
    val repositoryName: String? = null,
    val url: String? = null,
)

data class InstalledExtensionRecord(
    val sourceKey: String,
    val name: String,
    val version: String,
    val fileName: String,
    val originKind: String,
    val originRepositoryId: String? = null,
    val originRepositoryName: String? = null,
    val originUrl: String? = null,
)

sealed class ExtensionFailure(message: String, cause: Throwable? = null) : IllegalStateException(message, cause) {
    class AlreadyInstalled(val key: String) : ExtensionFailure("Extension already installed: $key")
    class MissingExtension(val key: String) : ExtensionFailure("Extension not installed: $key")
    class KeyMismatch(expected: String?, actual: String?) :
        ExtensionFailure("Expected source '$expected', got '${actual ?: "<none>"}'")
    class InvalidScript(cause: Throwable? = null) : ExtensionFailure("Invalid extension script${cause?.let { ": ${it.message}" } ?: ""}", cause)
    class StorageFailure(cause: Throwable) : ExtensionFailure("Extension storage failed: ${cause.message}", cause)
    class UpdateInProgress(val key: String) : ExtensionFailure("Update already in progress: $key")
    class AmbiguousSource(val key: String) : ExtensionFailure("Multiple catalog variants for key: $key")
    class MissingSource(val key: String) : ExtensionFailure("Source not in catalog: $key")
}
