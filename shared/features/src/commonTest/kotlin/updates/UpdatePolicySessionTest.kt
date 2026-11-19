package app.koyura.features.updates

import app.koyura.domain.updates.SmartUpdatePreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 智能更新策略：三态、整份覆盖写入与归一化、与引擎偏好模型逐字映射。
 */
class UpdatePolicySessionTest {
    @Test
    fun refreshReadsStoredPolicy() = runTest {
        val stored = UpdatePolicy(enabled = false, skipCompleted = false, excludedTitleTerms = listOf("外传"))
        val session = UpdatePolicySession(FakeUpdatePolicyRepository(stored))

        assertEquals(UpdatePolicyLoadState.Loading, session.loadState)
        session.refresh()

        assertEquals(UpdatePolicyLoadState.Ready, session.loadState)
        assertEquals(stored, session.policy)
    }

    @Test
    fun applyNormalizesExcludedTermsAndReReadsRepository() = runTest {
        val repository = FakeUpdatePolicyRepository()
        val session = UpdatePolicySession(repository)
        session.refresh()

        val saved = session.apply(
            UpdatePolicy(
                skipWithUnread = false,
                excludedTitleTerms = listOf("  外传 ", "", "外传", "Fanbook"),
            ),
        )

        assertTrue(saved)
        assertEquals(listOf("外传", "Fanbook"), repository.applied.single().excludedTitleTerms)
        assertEquals(listOf("外传", "Fanbook"), session.policy.excludedTitleTerms)
        assertFalse(session.policy.skipWithUnread)
    }

    @Test
    fun applyFailureKeepsCommittedPolicy() = runTest {
        val repository = FakeUpdatePolicyRepository()
        val session = UpdatePolicySession(repository)
        session.refresh()
        repository.applyFailure = "disk full"

        val saved = session.apply(UpdatePolicy(enabled = false))

        assertFalse(saved)
        assertEquals("disk full", session.actionError)
        // 写入失败不改已提交策略。
        assertTrue(session.policy.enabled)
    }

    @Test
    fun loadFailureReportsMessageAndRetainsDefaults() = runTest {
        val repository = FakeUpdatePolicyRepository()
        repository.readFailure = "unavailable"
        val session = UpdatePolicySession(repository)

        session.refresh()

        assertEquals("unavailable", (session.loadState as UpdatePolicyLoadState.Failed).message)
        assertEquals(UpdatePolicy(), session.policy)
    }

    @Test
    fun policyMapsOntoEnginePreferencesFieldByField() {
        val policy = UpdatePolicy(
            enabled = false,
            skipCompleted = false,
            skipWithUnread = false,
            skipNotStarted = false,
            skipOutsideReleasePeriod = false,
            excludedTitleTerms = listOf("外传"),
        )

        val preferences = policy.toPreferences()

        assertEquals(
            SmartUpdatePreferences(
                enabled = false,
                skipCompleted = false,
                skipWithUnread = false,
                skipNotStarted = false,
                skipOutsideReleasePeriod = false,
                excludedTitleTerms = listOf("外传"),
            ),
            preferences,
        )
        // 反向映射回到同一策略（引擎读回后界面状态不漂移）。
        assertEquals(policy, preferences.toUpdatePolicy())
        assertEquals(SmartUpdatePreferences(), SmartUpdatePreferences().toUpdatePolicy().toPreferences())
    }
}
