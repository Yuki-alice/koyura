package app.koyura.app.updates

import app.koyura.data.updates.LibraryUpdateScanner
import app.koyura.features.updates.UpdatePolicy
import app.koyura.features.updates.UpdatePolicyRepository
import app.koyura.features.updates.toPreferences
import app.koyura.features.updates.toUpdatePolicy

/**
 * 生产智能更新策略仓储：扫描引擎的偏好读写（`library_prefs` 持久化）到界面契约的唯一桥。
 * 字段经 features 的映射函数逐字对齐 [app.koyura.domain.updates.SmartUpdatePreferences]。
 */
class ProductionUpdatePolicyRepository(
    private val scanner: LibraryUpdateScanner,
) : UpdatePolicyRepository {

    override suspend fun policy(): UpdatePolicy = scanner.preferences().toUpdatePolicy()

    override suspend fun apply(policy: UpdatePolicy) {
        scanner.setPreferences(policy.toPreferences())
    }
}
