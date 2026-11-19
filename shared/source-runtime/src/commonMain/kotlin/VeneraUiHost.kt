package app.koyura.source.runtime

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString.Companion.decodeBase64

data class SourceDialogAction(val text: String, val style: String)

sealed interface SourceDialogImage {
    data class Url(val value: String) : SourceDialogImage
    data class Bytes(val value: ByteArray) : SourceDialogImage
}

/**
 * Presentation services for installed source scripts. Synchronous methods enqueue UI work
 * without blocking QuickJS. Suspending dialogs release their native presentation on cancellation.
 */
interface VeneraUiHost {
    fun log(sourceKey: String?, level: String, title: String, content: String) {}
    fun showMessage(sourceKey: String, message: String) {}
    fun launchUrl(sourceKey: String, url: String) {}
    fun showLoading(sourceKey: String, id: Int, onCancel: (() -> Unit)?) {}
    fun cancelLoading(sourceKey: String, id: Int) {}
    suspend fun showDialog(
        sourceKey: String,
        title: String,
        content: String,
        actions: List<SourceDialogAction>,
        runAction: suspend (Int) -> Unit,
    ) {}
    suspend fun showInputDialog(
        sourceKey: String,
        title: String,
        image: SourceDialogImage?,
        validate: (suspend (String) -> String?)?,
    ): String? = null
    suspend fun showSelectDialog(
        sourceKey: String,
        title: String,
        options: List<String>,
        initialIndex: Int?,
    ): Int? = null
    suspend fun setClipboard(sourceKey: String, text: String) {}
    suspend fun getClipboard(sourceKey: String): String? = null

    companion object {
        val Headless: VeneraUiHost = object : VeneraUiHost {}
    }
}

internal class InteractionHost(
    private val host: VeneraUiHost,
    private val scheduleCancellation: (Int) -> Unit,
    private val dispatchCancellation: suspend (Int) -> Unit,
    private val onCallback: suspend (Int, String) -> String?,
) {
    @Serializable
    private data class Action(val text: String, val style: String, val callback: Int? = null)

    @Serializable
    private data class Request(
        val type: String,
        val key: String? = null,
        val title: String = "",
        val content: String = "",
        val level: String = "",
        val id: Int? = null,
        val cancellable: Boolean = false,
        val callback: Int? = null,
        val imageUrl: String? = null,
        val imageBytes: String? = null,
        val options: List<String> = emptyList(),
        val initialIndex: Int? = null,
        val actions: List<Action> = emptyList(),
    )

    private class Loading(val owner: String, val cancellable: Boolean, var requested: Boolean = false)

    private val lock = SessionLock()
    private val loading = mutableMapOf<Int, Loading>()
    private val activeWaits = mutableMapOf<String, MutableSet<Channel<Int>>>()

    // A host call already carries QuickJS's active evaluation context. Use it
    // for cancellation callbacks while the source waits for HTTP or a delay.
    suspend fun <T> whileAwaiting(sourceKey: String?, block: suspend () -> T): T {
        if (sourceKey == null) return block()
        val events = lock.locked {
            if (loading.values.none { it.owner == sourceKey && it.cancellable }) return@locked null
            Channel<Int>(Channel.UNLIMITED).also { activeWaits.getOrPut(sourceKey, ::linkedSetOf).add(it) }
        } ?: return block()
        return coroutineScope {
            val watcher = launch {
                for (id in events) {
                    try {
                        dispatchCancellation(id)
                    } catch (cancelled: CancellationException) {
                        scheduleCancellation(id)
                        throw cancelled
                    } catch (_: Exception) {
                        // The script callback cannot fail the HTTP or delay it interrupted.
                    }
                }
            }
            try {
                block()
            } finally {
                lock.locked {
                    activeWaits[sourceKey]?.let { waits ->
                        waits.remove(events)
                        if (waits.isEmpty()) activeWaits.remove(sourceKey)
                    }
                    events.close()
                }
                watcher.invokeOnCompletion {
                    while (true) scheduleCancellation(events.tryReceive().getOrNull() ?: break)
                }
            }
        }
    }

    private fun requestCancellation(id: Int) {
        val schedule = lock.locked {
            val current = loading[id] ?: return@locked false
            if (!current.cancellable || current.requested) return@locked false
            current.requested = true
            activeWaits[current.owner]?.lastOrNull()?.trySend(id)?.isSuccess != true
        }
        if (schedule) scheduleCancellation(id)
    }

    fun post(payload: String): Boolean {
        val request = Json.decodeFromString<Request>(payload)
        val key = request.key
        when (request.type) {
            "log" -> host.log(key, request.level, request.title, request.content)
            "message" -> host.showMessage(checkNotNull(key), request.content)
            "launch" -> host.launchUrl(checkNotNull(key), requireHttpUrl(request.content))
            "loading" -> {
                val id = checkNotNull(request.id)
                val owner = checkNotNull(key)
                lock.locked { loading[id] = Loading(owner, request.cancellable) }
                try {
                    host.showLoading(owner, id, if (request.cancellable) {
                        { requestCancellation(id) }
                    } else null)
                } catch (error: Exception) {
                    lock.locked { loading.remove(id) }
                    throw error
                }
            }
            "cancelLoading" -> {
                val id = checkNotNull(request.id)
                val owner = lock.locked { loading[id]?.owner?.takeIf { it == key }?.also { loading.remove(id) } }
                if (owner != null) host.cancelLoading(owner, id)
            }
        }
        return true
    }

    suspend fun interact(payload: String): String {
        val request = Json.decodeFromString<Request>(payload)
        val key = checkNotNull(request.key)
        return when (request.type) {
            "dialog" -> {
                var accepted = false
                val gate = SessionLock()
                host.showDialog(key, request.title, request.content, request.actions.map { SourceDialogAction(it.text, it.style) }) { index ->
                    val action = request.actions.getOrNull(index) ?: return@showDialog
                    val first = gate.locked { (!accepted).also { accepted = true } }
                    if (first) action.callback?.let { onCallback(it, "null") }
                }
                "null"
            }
            "input" -> {
                val image = request.imageUrl?.let { SourceDialogImage.Url(requireHttpUrl(it)) }
                    ?: request.imageBytes?.let { SourceDialogImage.Bytes(requireNotNull(it.decodeBase64()).toByteArray()) }
                val validate: (suspend (String) -> String?)? = request.callback?.let { id ->
                    { text: String -> onCallback(id, JsonPrimitive(text).toString()) }
                }
                host.showInputDialog(key, request.title, image, validate)?.let { JsonPrimitive(it).toString() } ?: "null"
            }
            "select" -> host.showSelectDialog(key, request.title, request.options, request.initialIndex)
                ?.takeIf { it in request.options.indices }?.toString() ?: "null"
            "setClipboard" -> {
                host.setClipboard(key, request.content)
                "null"
            }
            "getClipboard" -> host.getClipboard(key)?.let { JsonPrimitive(it).toString() } ?: "null"
            else -> error("Unknown source interaction")
        }
    }

    fun close() {
        val active = lock.locked { loading.toMap().also { loading.clear() } }
        active.forEach { (id, current) -> runCatching { host.cancelLoading(current.owner, id) } }
    }

    private fun requireHttpUrl(raw: String): String {
        val url = Url(raw)
        require("://" in raw && url.protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) &&
            url.host.isNotBlank() && url.user == null && url.password == null) { "Invalid source URL" }
        return raw
    }
}
