package app.koyura.features.settings

/**
 * 内存版设置仓储，供预览、测试与未装配偏好存储时使用。
 *
 * 每次写回整份快照，读回时返回同一份不可变 data class 值，便于断言「保存后立即生效」。
 */
class FakeSettingsRepository(
    initial: SettingsPreferences = SettingsPreferences(),
) : SettingsRepository {
    private var current: SettingsPreferences = initial
    var saveCount: Int = 0
        private set

    override fun load(): SettingsPreferences = current

    override fun save(preferences: SettingsPreferences) {
        current = preferences
        saveCount += 1
    }
}
