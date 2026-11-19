package app.koyura.domain.extension

enum class SourceAccountFieldType { TEXT, PASSWORD, COOKIES }

data class SourceAccountField(val label: String, val type: SourceAccountFieldType)

data class SourceAccountInfoConfig(val title: String, val hasValue: Boolean)

data class SourceAccountInfo(val title: String, val value: String?)

data class SourceAccountConfig(
    val fields: List<SourceAccountField>,
    val registerWebsite: String?,
    val declaresWebLogin: Boolean,
    val infoItems: List<SourceAccountInfoConfig>,
)

enum class SourceLoginState { LOGGED_OUT, FORM, WEB }
