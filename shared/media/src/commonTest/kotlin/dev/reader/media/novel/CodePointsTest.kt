package dev.reader.media.novel

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Code point arithmetic must treat an emoji or supplementary-plane character as one unit, never as the
 * two UTF-16 code units Kotlin strings use.
 */
class CodePointsTest {

    @Test
    fun countsBmpTextAsOnePerChar() {
        assertEquals(3, CodePoints.length("abc"))
        assertEquals(3, CodePoints.length("中文测"))
        assertEquals(0, CodePoints.length(""))
    }

    @Test
    fun countsSupplementaryPlaneAndEmojiAsSingleCodePoints() {
        // 😀 is U+1F600 (surrogate pair); 𠮷 is U+20BB7.
        assertEquals(1, CodePoints.length("😀"))
        assertEquals(1, CodePoints.length("\uD842\uDFB7"))
        assertEquals(4, CodePoints.length("a😀中𠮷"))
        // Sanity: UTF-16 length differs, which is the whole point.
        assertEquals(6, "a😀中𠮷".length)
    }

    @Test
    fun convertsCodePointOffsetToStringIndexAroundSurrogates() {
        val text = "a😀b"
        assertEquals(0, CodePoints.toStringIndex(text, 0))
        assertEquals(1, CodePoints.toStringIndex(text, 1))
        // After the emoji is index 3, not 2.
        assertEquals(3, CodePoints.toStringIndex(text, 2))
        assertEquals(4, CodePoints.toStringIndex(text, 3))
    }

    @Test
    fun convertsStringIndexToCodePointOffset() {
        val text = "a😀b"
        assertEquals(0, CodePoints.toCodePointOffset(text, 0))
        assertEquals(1, CodePoints.toCodePointOffset(text, 1))
        // Index 2 would be mid-surrogate; 3 is the boundary after 😀.
        assertEquals(2, CodePoints.toCodePointOffset(text, 3))
        assertEquals(3, CodePoints.toCodePointOffset(text, 4))
    }

    @Test
    fun clampsOutOfRangeOffsetsToParagraphBounds() {
        val text = "a😀"
        assertEquals(0, CodePoints.clamp(text, -5))
        assertEquals(2, CodePoints.clamp(text, 99))
        // toStringIndex returns a UTF-16 index: the end of "a😀" is 3, since 😀 is two code units.
        assertEquals(3, CodePoints.toStringIndex(text, 99))
        assertEquals(0, CodePoints.toStringIndex(text, -1))
    }

    @Test
    fun codePointOffsetNeverLandsInsideASurrogatePair() {
        val text = "x😀y"
        for (offset in 0..CodePoints.length(text)) {
            val index = CodePoints.toStringIndex(text, offset)
            if (index < text.length) {
                val isLowSurrogate = text[index].isLowSurrogate()
                // A legal index may point at a low surrogate only when it is the pair's own start+1,
                // which never happens for a code point boundary.
                assertEquals(false, isLowSurrogate, "offset $offset landed on a low surrogate")
            }
        }
    }

    @Test
    fun fingerprintIgnoresWhitespaceButNotContent() {
        assertEquals(NovelFingerprints.block("hello  world"), NovelFingerprints.block("hello world"))
        assertEquals(NovelFingerprints.block("  中文  段落 "), NovelFingerprints.block("中文 段落"))
        // A real content change must change the fingerprint.
        assertEquals(false, NovelFingerprints.block("hello world") == NovelFingerprints.block("hello worlds"))
    }
}
