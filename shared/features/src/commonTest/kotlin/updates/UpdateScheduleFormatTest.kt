package app.koyura.features.updates

import kotlin.test.Test
import kotlin.test.assertTrue

class UpdateScheduleFormatTest {
    @Test
    fun formatsMillisAsMinutePrecisionDateTime() {
        // 时区相关：只断言形状「yyyy-MM-dd HH:mm」，不锁定时区换算值。
        val text = formatScheduleMillis(1_800_000_000_000)
        assertTrue(text.matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")), text)
    }
}
