package app.koyura.features.account

import androidx.compose.runtime.staticCompositionLocalOf

/** 注入的账号仓储；未装配时（测试与预览）各屏回落到 Fake。 */
val LocalAccountRepository = staticCompositionLocalOf<AccountRepository?> { null }
