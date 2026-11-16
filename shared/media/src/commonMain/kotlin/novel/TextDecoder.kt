package app.koyura.media.novel

/**
 * Result of decoding a novel file into text.
 *
 * [encoding] is the encoding that was used; no byte order mark remains in [text].
 */
internal data class DecodedText(
    val encoding: TextEncoding,
    val text: String,
)

/**
 * Decodes novel bytes into a [String] while strictly validating the encoding.
 *
 * Kotlin's `String(bytes, Charsets.UTF_8)` replaces malformed sequences with U+FFFD, which would
 * silently corrupt chapters. This decoder instead reports the exact byte offset and line of the
 * first malformed sequence through [NovelImportException.Encoding]. Byte offsets are relative to the
 * whole file, so a reported offset can be checked against the file directly.
 */
internal object TextDecoder {

    private const val UTF8_BOM_1 = 0xEF
    private const val UTF8_BOM_2 = 0xBB
    private const val UTF8_BOM_3 = 0xBF
    private const val UTF16_LE_BOM_0 = 0xFF
    private const val UTF16_LE_BOM_1 = 0xFE
    private const val UTF16_BE_BOM_0 = 0xFE
    private const val UTF16_BE_BOM_1 = 0xFF

    /**
     * Decodes [bytes] whose file path is [path].
     *
     * Detection order: empty → known binary containers → UTF-32 BOM → UTF-8 BOM → UTF-16 BOMs →
     * NUL-heavy bytes → strict UTF-8.
     *
     * @throws NovelImportException.Empty for zero-length input.
     * @throws NovelImportException.UnsupportedFormat for a recognizable non-text container.
     * @throws NovelImportException.Encoding for malformed text.
     */
    fun decode(path: String, bytes: ByteArray): DecodedText {
        if (bytes.isEmpty()) {
            throw NovelImportException.Empty(path = path, byteSize = 0)
        }
        detectUnsupportedContainer(path, bytes)?.let { throw it }

        // UTF-32 wrappers share the UTF-16 BOM prefix; reject them before the UTF-16 branch.
        detectUtf32Bom(path, bytes)?.let { throw it }

        if (bytes.size >= 3 && bytes.u8(0) == UTF8_BOM_1 && bytes.u8(1) == UTF8_BOM_2 && bytes.u8(2) == UTF8_BOM_3) {
            return DecodedText(
                encoding = TextEncoding.UTF_8,
                text = decodeUtf8(path, bytes, start = 3),
            )
        }
        if (bytes.size >= 2 && bytes.u8(0) == UTF16_LE_BOM_0 && bytes.u8(1) == UTF16_LE_BOM_1) {
            return DecodedText(
                encoding = TextEncoding.UTF_16_LE,
                text = decodeUtf16(path, bytes, start = 2, littleEndian = true),
            )
        }
        if (bytes.size >= 2 && bytes.u8(0) == UTF16_BE_BOM_0 && bytes.u8(1) == UTF16_BE_BOM_1) {
            return DecodedText(
                encoding = TextEncoding.UTF_16_BE,
                text = decodeUtf16(path, bytes, start = 2, littleEndian = false),
            )
        }

        detectNulHeavyText(path, bytes)?.let { throw it }
        detectBomlessUtf16(path, bytes)?.let { throw it }

        val text = decodeUtf8(path, bytes, start = 0)
        // Some BOM-less UTF-16 byte sequences are valid UTF-8 by accident. Decoding them yields text
        // salted with C0 control characters (every other byte of a CJK code unit lands in 0x00..0x1F),
        // which no real novel contains. Reject that rather than serving mojibake.
        detectControlCharacterGarbage(path, text)?.let { throw it }
        return DecodedText(
            encoding = TextEncoding.UTF_8,
            text = text,
        )
    }

    /**
     * Recognizes magic numbers of common containers that are not plain text novels.
     *
     * A `.txt` path holding a ZIP (an EPUB renamed by the user) is a format problem, not an encoding
     * problem, so the caller can route it to the right importer.
     */
    private fun detectUnsupportedContainer(path: String, bytes: ByteArray): NovelImportException.UnsupportedFormat? {
        if (bytes.size >= 4 && bytes.u8(0) == 0x50 && bytes.u8(1) == 0x4B &&
            (bytes.u8(2) == 0x03 || bytes.u8(2) == 0x05 || bytes.u8(2) == 0x07) &&
            (bytes.u8(3) == 0x04 || bytes.u8(3) == 0x06 || bytes.u8(3) == 0x08)
        ) {
            return NovelImportException.UnsupportedFormat(
                path = path,
                format = "zip-container",
                detectedSignature = hex(bytes, 4),
            )
        }
        if (bytes.size >= 4 && bytes.u8(0) == 0x25 && bytes.u8(1) == 0x50 &&
            bytes.u8(2) == 0x44 && bytes.u8(3) == 0x46
        ) {
            return NovelImportException.UnsupportedFormat(
                path = path,
                format = "pdf",
                detectedSignature = hex(bytes, 4),
            )
        }
        if (bytes.size >= 2 && bytes.u8(0) == 0x1F && bytes.u8(1) == 0x8B) {
            return NovelImportException.UnsupportedFormat(
                path = path,
                format = "gzip",
                detectedSignature = hex(bytes, 2),
            )
        }
        return null
    }

    private fun hex(bytes: ByteArray, count: Int): String {
        val builder = StringBuilder(count * 2)
        for (index in 0 until count) {
            val value = bytes.u8(index)
            builder.append(HEX_DIGITS[value shr 4]).append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX_DIGITS = "0123456789abcdef"

    /**
     * Rejects decoded text containing control characters that never appear in prose.
     *
     * BOM-less UTF-16 files whose high byte falls in 0x00..0x1F decode as valid UTF-8 but produce a C0
     * control character per such code unit. Real text uses only tab, LF, CR and form feed; any other
     * C0/C1 control character means the bytes were not UTF-8 text, so serving them would be silent
     * mojibake. The check is deliberately strict, because a wrong-but-readable book is worse than a
     * clear encoding error.
     */
    private fun detectControlCharacterGarbage(
        path: String,
        text: String,
    ): NovelImportException.Encoding? {
        for (char in text) {
            val code = char.code
            val isAllowedTextWhitespace = code == 0x09 || code == 0x0A || code == 0x0C || code == 0x0D
            if ((code < 0x20 && !isAllowedTextWhitespace) || code in 0x7F..0x9F) {
                return NovelImportException.Encoding(
                    path = path,
                    byteOffset = null,
                    line = null,
                    encoding = TextEncoding.UTF_8.displayName,
                )
            }
        }
        return null
    }

    private fun detectUtf32Bom(path: String, bytes: ByteArray): NovelImportException.UnsupportedFormat? {
        if (bytes.size < 4) return null
        if (bytes.u8(0) == 0xFF && bytes.u8(1) == 0xFE && bytes.u8(2) == 0x00 && bytes.u8(3) == 0x00) {
            return NovelImportException.UnsupportedFormat(path, "utf-32le", "fffe0000")
        }
        if (bytes.u8(0) == 0x00 && bytes.u8(1) == 0x00 && bytes.u8(2) == 0xFE && bytes.u8(3) == 0xFF) {
            return NovelImportException.UnsupportedFormat(path, "utf-32be", "0000feff")
        }
        return null
    }

    /**
     * Rejects text that is really UTF-16 without a BOM (or other NUL-filled binary).
     *
     * Strict UTF-8 accepts NUL bytes, which would turn BOM-less UTF-16 into invisible garbage.
     * A file that is at least a quarter NUL bytes is not a readable text novel.
     */
    private fun detectNulHeavyText(path: String, bytes: ByteArray): NovelImportException.UnsupportedFormat? {
        if (bytes.size < 8) return null
        var nuls = 0
        for (byte in bytes) {
            if (byte.toInt() == 0) nuls++
        }
        if (nuls * 4 >= bytes.size) {
            return NovelImportException.UnsupportedFormat(
                path = path,
                format = "utf-16-without-bom",
                detectedSignature = "nul-heavy",
            )
        }
        return null
    }

    /**
     * Rejects text that is really UTF-16 without a BOM.
     *
     * BOM-less UTF-16 has NUL bytes in every other position for the common (ASCII/BMP) range. A file
     * with NULs concentrated on one parity and few on the other is far more likely UTF-16 than text,
     * so it is reported as unsupported rather than decoded into invisible garbage. Returns `null`
     * when the shape is ambiguous, which keeps ordinary UTF-8 with occasional NUL bytes working.
     */
    private fun detectBomlessUtf16(path: String, bytes: ByteArray): NovelImportException.UnsupportedFormat? {
        if (bytes.size < 8 || bytes.size % 2 != 0) return null
        var evenNuls = 0
        var oddNuls = 0
        for (index in bytes.indices) {
            if (bytes[index].toInt() != 0) continue
            if (index % 2 == 0) evenNuls++ else oddNuls++
        }
        val pairs = bytes.size / 2
        val oneSide = if (evenNuls >= oddNuls) evenNuls else oddNuls
        val otherSide = pairs - oneSide
        // Most code units have a NUL byte on one side, and the other side is nearly NUL-free.
        if (oneSide * 10 >= pairs * 6 && otherSide * 20 <= pairs) {
            return NovelImportException.UnsupportedFormat(
                path = path,
                format = "utf-16-without-bom",
                detectedSignature = "nul-parity",
            )
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // UTF-8
    // ---------------------------------------------------------------------------------------------

    private fun decodeUtf8(path: String, bytes: ByteArray, start: Int): String {
        val out = StringBuilder(bytes.size - start)
        var index = start
        var line = 1
        while (index < bytes.size) {
            val first = bytes.u8(index)
            if (first < 0x80) {
                out.append(first.toChar())
                if (first == 0x0A) line++
                index++
                continue
            }

            val length = utf8SequenceLength(first)
            if (length == 0 || index + length > bytes.size) {
                throw encodingError(path, TextEncoding.UTF_8, index, line)
            }

            var codePoint = when (length) {
                2 -> first and 0x1F
                3 -> first and 0x0F
                4 -> first and 0x07
                else -> throw encodingError(path, TextEncoding.UTF_8, index, line)
            }
            var valid = true
            var offset = 1
            while (offset < length) {
                val next = bytes.u8(index + offset)
                if (next and 0xC0 != 0x80) {
                    valid = false
                    break
                }
                codePoint = (codePoint shl 6) or (next and 0x3F)
                offset++
            }
            if (!valid || !isValidUtf8CodePoint(codePoint, length)) {
                throw encodingError(path, TextEncoding.UTF_8, index, line)
            }

            if (codePoint <= 0xFFFF) {
                out.append(codePoint.toChar())
            } else {
                val value = codePoint - 0x10000
                out.append((0xD800 + (value shr 10)).toChar())
                out.append((0xDC00 + (value and 0x3FF)).toChar())
            }
            index += length
        }
        return out.toString()
    }

    /** Length in bytes of a UTF-8 sequence from its lead byte, or 0 when the lead byte is illegal. */
    private fun utf8SequenceLength(lead: Int): Int = when (lead) {
        in 0xC2..0xDF -> 2
        in 0xE0..0xEF -> 3
        in 0xF0..0xF4 -> 4
        else -> 0
    }

    /** Enforces the overlong/surrogate/range rules that the byte-shape check does not cover. */
    private fun isValidUtf8CodePoint(codePoint: Int, length: Int): Boolean = when (length) {
        2 -> codePoint >= 0x80
        3 -> codePoint >= 0x800 && codePoint !in 0xD800..0xDFFF
        4 -> codePoint in 0x10000..0x10FFFF
        else -> false
    }

    // ---------------------------------------------------------------------------------------------
    // UTF-16
    // ---------------------------------------------------------------------------------------------

    private fun decodeUtf16(path: String, bytes: ByteArray, start: Int, littleEndian: Boolean): String {
        val length = bytes.size - start
        val encoding = encodingOf(littleEndian)
        val out = StringBuilder(length / 2)
        var index = start
        var line = 1
        while (index + 1 < bytes.size) {
            val unit = codeUnitAt(bytes, index, littleEndian)
            if (unit in 0xD800..0xDBFF) {
                if (index + 3 >= bytes.size) {
                    // High surrogate without room for a low one: report the truncated code unit.
                    throw encodingError(path, encoding, index, line)
                }
                val low = codeUnitAt(bytes, index + 2, littleEndian)
                if (low !in 0xDC00..0xDFFF) {
                    throw encodingError(path, encoding, index, line)
                }
                out.append(unit.toChar())
                out.append(low.toChar())
                index += 4
                continue
            }
            if (unit in 0xDC00..0xDFFF) {
                throw encodingError(path, encoding, index, line)
            }
            out.append(unit.toChar())
            if (unit == 0x000A) line++
            index += 2
        }
        if (index < bytes.size) {
            // One trailing byte cannot form a code unit. Report its index and the line reached so far,
            // which matches the "start of the malformed sequence / line of the failure" contract.
            throw encodingError(path, encoding, index, line)
        }
        return out.toString()
    }

    private fun codeUnitAt(bytes: ByteArray, index: Int, littleEndian: Boolean): Int =
        if (littleEndian) {
            bytes.u8(index) or (bytes.u8(index + 1) shl 8)
        } else {
            (bytes.u8(index) shl 8) or bytes.u8(index + 1)
        }

    private fun encodingOf(littleEndian: Boolean): TextEncoding =
        if (littleEndian) TextEncoding.UTF_16_LE else TextEncoding.UTF_16_BE

    /** Byte offset is the start of the malformed sequence, relative to the whole file. */
    private fun encodingError(
        path: String,
        encoding: TextEncoding,
        byteOffset: Int,
        line: Int,
    ): NovelImportException.Encoding =
        NovelImportException.Encoding(
            path = path,
            byteOffset = byteOffset.toLong(),
            line = line,
            encoding = encoding.displayName,
        )

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
