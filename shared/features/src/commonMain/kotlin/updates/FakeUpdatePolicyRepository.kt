package app.koyura.features.updates

/**
 * 策略内存实现（测试与无引擎装配共用）：读回最近一次 `apply` 写入的值。
 * 与 `FakeUpdatesRepository` 同一写法，`failure` 注入用于三态测试。
 */
class FakeUpdatePolicyRepository(
    private var stored: UpdatePolicy = UpdatePolicy(),
) : UpdatePolicyRepository {
    var readCount: Int = 0
        private set

    var applyCount: Int = 0
        private set

    /** 写入的完整历史（断言归一化与整份覆盖）。 */
    val applied: MutableList<UpdatePolicy> = mutableListOf()

    /** 有值时下一次读取抛错（用后即焚）。 */
    var readFailure: String? = null

    /** 有值时下一次写入抛错（用后即焚）。 */
    var applyFailure: String? = null

    override suspend fun policy(): UpdatePolicy {
        readCount += 1
        readFailure?.let { message ->
            readFailure = null
            throw IllegalStateException(message)
        }
        return stored
    }

    override suspend fun apply(policy: UpdatePolicy) {
        applyFailure?.let { message ->
            applyFailure = null
            throw IllegalStateException(message)
        }
        applyCount += 1
        stored = policy
        applied += policy
    }
}
