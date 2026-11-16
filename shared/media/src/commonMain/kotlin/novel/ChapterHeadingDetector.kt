package app.koyura.media.novel

/**
 * A chapter heading found on one line of a TXT file.
 *
 * [title] is the whole trimmed line as the file wrote it, including the numeric prefix
 * (`第一章 夜雨`) and any full-width digits. That keeps the table of contents faithful to the
 * author and avoids losing a title that only exists as an inline suffix.
 */
internal data class HeadingMatch(
    val title: String,
    val level: Int,
)

/**
 * Detects Chinese chapter headings in TXT novels.
 *
 * Supported forms (all optionally followed by an inline title such as `第一章 夜雨` or
 * `第一章：夜雨`):
 * - Numbered units: `第一章`, `第1章`, `第一节`, `第二回`, `第一卷`, `第二部`, `第三篇`, and the
 *   traditional `第1節`; full-width digits such as `第１章` are accepted.
 * - Volume prefix form: `卷一`, `卷1`, `卷一 少年游`.
 * - Named sections: `序`, `序章`, `序言`, `引子`, `楔子`, `前言`, `后记`, `终章`, `終章`, `尾声`,
 *   `跋`, `番外`, `番外篇`, `番外一`.
 *
 * Volumes (`卷`/`部`/`篇`) are level `0`; readable chapters are level `1`. The detector stays
 * conservative: a line only counts as a heading when the numeric token is followed by a separator
 * or the end of the line, so ordinary prose such as `第三天他走了。` never matches. Lines that look
 * like a heading but carry a sentence-ending clause (`第一章 ... 。`) are treated as body text,
 * because splitting a paragraph is worse than missing a heading.
 */
internal object ChapterHeadingDetector {

    /** Maximum heading length; longer matches are prose that happens to start with a token. */
    private const val MAX_HEADING_LENGTH = 40

    /** Numerals accepted in `第…章` and `卷…`. */
    private const val NUMERALS = "0-9零〇一二三四五六七八九十百千万两"

    /** Separators allowed between a heading token and its inline title. */
    private const val TITLE_SEPARATOR = "[\\s\\u3000:：、.·—–-]+"

    private val numbered = Regex(
        "^(第[$NUMERALS]+[章节節回卷部篇])(?:$TITLE_SEPARATOR(.*))?$",
    )

    private val volumePrefix = Regex(
        "^(卷[$NUMERALS]+)(?:$TITLE_SEPARATOR(.*))?$",
    )

    /**
     * `序` alone is last in the alternation so the longer forms win the match, and `番外` accepts an
     * optional `篇` or numeral suffix.
     */
    private val named = Regex(
        "^(序章|序言|引子|楔子|前言|后记|終章|终章|尾声|跋|番外(?:篇|[$NUMERALS]+)?|序)" +
            "(?:$TITLE_SEPARATOR(.*))?$",
    )

    /**
     * Returns the heading on [rawLine], or `null` when the line is body text.
     *
     * [rawLine] should already have its line terminator removed; leading indentation is tolerated.
     * The returned [HeadingMatch.title] preserves the original characters, including full-width
     * digits, so the table of contents matches the file.
     */
    fun detect(rawLine: String): HeadingMatch? {
        val line = rawLine.trim { it == ' ' || it == '\t' || it == '\u3000' || it == '\uFEFF' }
        if (line.isEmpty() || line.length > MAX_HEADING_LENGTH) return null

        // Full-width digits (U+FF10..U+FF19) are common in older files; match on ASCII internally.
        val normalized = normalizeFullWidthDigits(line)

        numbered.matchEntire(normalized)?.let { match ->
            if (match.groupValues[2].trim().containsInvalidTitlePunctuation()) return null
            val unit = match.groupValues[1].last()
            val level = if (unit == '卷' || unit == '部' || unit == '篇') 0 else 1
            return HeadingMatch(title = line, level = level)
        }

        volumePrefix.matchEntire(normalized)?.let { match ->
            if (match.groupValues[2].trim().containsInvalidTitlePunctuation()) return null
            return HeadingMatch(title = line, level = 0)
        }

        named.matchEntire(normalized)?.let { match ->
            if (match.groupValues[2].trim().containsInvalidTitlePunctuation()) return null
            return HeadingMatch(title = line, level = 1)
        }

        return null
    }

    /**
     * A `。` (or `；`/`？`) inside the inline suffix means the line is a sentence, not a title.
     *
     * `、` and `！` stay allowed because they legitimately appear in chapter titles.
     */
    private fun String.containsInvalidTitlePunctuation(): Boolean =
        any { it == '。' || it == '；' || it == '？' }

    /** Maps full-width digits to ASCII so one pattern covers `第１章` and `第1章`. */
    private fun normalizeFullWidthDigits(line: String): String {
        if (line.none { it in '\uFF10'..'\uFF19' }) return line
        return buildString(line.length) {
            for (char in line) {
                append(if (char in '\uFF10'..'\uFF19') '0' + (char - '\uFF10') else char)
            }
        }
    }

    /** Indentation and separator characters trimmed from prose lines. */
    fun trimProseIndent(line: String): String =
        line.trim { it == ' ' || it == '\t' || it == '\u3000' || it == '\uFEFF' }
}
