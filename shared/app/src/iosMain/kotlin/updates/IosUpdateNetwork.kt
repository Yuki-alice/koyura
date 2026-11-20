package app.koyura.app.updates

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
internal object IosUpdateNetwork {
    private data class Connectivity(val online: Boolean, val unmetered: Boolean)

    private val connectivity = MutableStateFlow<Connectivity?>(null)
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("library-update-network", null))
        nw_path_monitor_set_update_handler(monitor) { path ->
            val online = nw_path_get_status(path) == nw_path_status_satisfied
            connectivity.value = Connectivity(online, online && !nw_path_is_expensive(path) && !nw_path_is_constrained(path))
        }
        nw_path_monitor_start(monitor)
    }

    val changes = connectivity.filterNotNull().map { Unit }

    fun isOnline() = connectivity.value?.online == true

    fun isUnmetered() = connectivity.value?.unmetered == true

    suspend fun awaitInitialPath() {
        withTimeoutOrNull(2_000) { connectivity.filterNotNull().first() }
    }
}
