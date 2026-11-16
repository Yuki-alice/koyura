package app.koyura.source.runtime

internal class VeneraVersion private constructor(
    private val numbers: List<Long>,
    private val suffix: String?,
) : Comparable<VeneraVersion> {
    override fun compareTo(other: VeneraVersion): Int {
        for (index in numbers.indices) {
            val order = numbers[index].compareTo(other.numbers[index])
            if (order != 0) return order
        }
        return when {
            suffix == other.suffix -> 0
            suffix == null -> if (other.suffix == "hotfix") -1 else 1
            other.suffix == null -> if (suffix == "hotfix") 1 else -1
            else -> suffix.compareTo(other.suffix)
        }
    }

    companion object {
        private val pattern = Regex("""\d+\.\d+\.\d+(?:[.-]\w[^\s]*)?""")

        fun parse(version: String): VeneraVersion {
            require(pattern.matches(version)) { "Invalid Venera version: '$version'" }
            val parts = version.replaceFirst('-', '.').split('.')
            val numbers = parts.take(3).map { part ->
                requireNotNull(part.toLongOrNull()) { "Invalid Venera version: '$version'" }
            }
            return VeneraVersion(numbers, parts.getOrNull(3))
        }
    }
}
