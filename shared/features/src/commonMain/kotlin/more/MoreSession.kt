package app.koyura.features.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 更多页状态座：两个开关的页内状态（随页签保留，不进返回栈）。
 *
 * 本轮 Fake 持有：Downloaded only 与隐身模式只驱动本页开关视觉，
 * 真实过滤/历史暂停由引擎轨与数据层接入后订阅同一状态源。
 */
class MoreSession {
    var downloadedOnly: Boolean by mutableStateOf(false)
        private set

    var incognito: Boolean by mutableStateOf(false)
        private set

    fun updateDownloadedOnly(value: Boolean) {
        downloadedOnly = value
    }

    fun updateIncognito(value: Boolean) {
        incognito = value
    }
}

@Composable
fun rememberMoreSession(): MoreSession = remember { MoreSession() }
