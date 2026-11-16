package dev.reader.media.epub

import dev.reader.media.novel.NovelImportException

/**
 * Resolves and normalizes references found inside an EPUB archive.
 *
 * References are resolved against the containing document, then normalized to an archive-root
 * relative path with `/` separators, for example `EPUB/text/chapter-1.xhtml` + `../images/a b.jpg`
 * becomes `EPUB/images/a b.jpg`.
 *
 * Hard rules, all enforced before any filesystem access:
 * - percent escapes are decoded **once**, as UTF-8, so `%20` and Chinese UTF-8 escapes such as
 *   `%E6%B8%A1` become real path text;
 * - `.` and `..` are folded lexically; a `..` that would climb above the archive root is rejected;
 * - a leading `/` (absolute), a URL scheme such as `http:`/`https:`/`data:`, or a protocol-relative
 *   `//host` reference is rejected;
 * - a fragment (`#anchor`) and query string are stripped from the path but reported separately.
 *
 * The resolver never touches the filesystem, so an unsafe reference is rejected even when the target
 * does not exist. Callers that must tolerate unsafe references (for example a remote `<img>` that
 * should simply be skipped) catch [NovelImportException.UnsafeReference].
 */
internal object EpubPathResolver {

    /** A resolved reference: normalized path plus the fragment that was stripped. */
    data class Resolved(
        val path: String,
        val fragment: String?,
    )

    /**
     * Resolves [reference] found in [documentPath].
     *
     * @param archivePath the archive file, used only to build error messages.
     * @param documentPath normalized path of the document containing the reference, or `null` when the
     *   reference must be archive-root relative (for example a container full-path).
     * @throws NovelImportException.UnsafeReference for absolute, remote, escaping or malformed refs.
     */
    fun resolve(archivePath: String, documentPath: String?, reference: String): Resolved {
        val raw = reference.trim()
        if (raw.isEmpty()) {
            throw unsafe(archivePath, reference, documentPath, "empty")
        }
        if (raw.startsWith("//")) {
            throw unsafe(archivePath, reference, documentPath, "remote")
        }
        val scheme = schemeOf(raw)
        if (scheme != null) {
            throw unsafe(archivePath, reference, documentPath, "remote")
        }
        if (raw.startsWith("/") || raw.startsWith("\\\\")) {
            throw unsafe(archivePath, reference, documentPath, "absolute")
        }

        val hashIndex = raw.indexOf('#')
        val fragment = if (hashIndex >= 0) raw.substring(hashIndex + 1).ifEmpty { null } else null
        val beforeFragment = if (hashIndex >= 0) raw.substring(0, hashIndex) else raw

        val queryIndex = beforeFragment.indexOf('?')
        val encodedPath = if (queryIndex >= 0) beforeFragment.substring(0, queryIndex) else beforeFragment
        if (encodedPath.isEmpty()) {
            throw unsafe(archivePath, reference, documentPath, "empty")
        }

        val decoded = percentDecode(archivePath, reference, documentPath, encodedPath)
        if (decoded.any { it.isControlOrDel() }) {
            // NUL, tab, newline and similar characters cannot appear in a ZIP entry name as typed
            // and would produce a path that can never be matched; reject rather than normalize.
            throw unsafe(archivePath, reference, documentPath, "control-character")
        }

        val segments = mutableListOf<String>()
        if (documentPath != null) {
            segments += documentPath.split('/').dropLast(1).filter { it.isNotEmpty() }
        }
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (segments.isEmpty()) {
                        throw unsafe(archivePath, reference, documentPath, "escapes-root")
                    }
                    segments.removeAt(segments.lastIndex)
                }
                else -> segments += segment
            }
        }
        if (segments.isEmpty()) {
            throw unsafe(archivePath, reference, documentPath, "empty")
        }
        return Resolved(path = segments.joinToString("/"), fragment = fragment)
    }

    /** Resolves and requires that the reference carries no fragment; used for structural paths. */
    fun resolveStructural(archivePath: String, documentPath: String?, reference: String): String {
        val resolved = resolve(archivePath, documentPath, reference)
        if (resolved.fragment != null) {
            throw unsafe(archivePath, reference, documentPath, "unexpected-fragment")
        }
        return resolved.path
    }

    /** `true` when [reference] looks remote; used to skip remote images without throwing. */
    fun isRemote(reference: String): Boolean {
        val raw = reference.trim()
        return raw.startsWith("//") || schemeOf(raw) != null
    }

    /** Detects a URL scheme (RFC 3986 `scheme ":"`), which always means an external reference. */
    private fun schemeOf(raw: String): String? {
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        val candidate = raw.substring(0, colon)
        val first = candidate.first()
        if (!(first.isLetter())) return null
        val valid = candidate.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
        return if (valid) candidate.lowercase() else null
    }

    /**
     * Decodes `%XX` escapes as UTF-8.
     *
     * Decoding is single-pass on purpose: a literal `%2520` becomes `%20`, not a space, which matches
     * how ZIP entry names are stored. Invalid escapes and invalid UTF-8 are rejected rather than
     * silently producing U+FFFD.
     */
    private fun percentDecode(
        archivePath: String,
        reference: String,
        documentPath: String?,
        encoded: String,
    ): String {
        if (!encoded.contains('%')) return encoded

        val bytes = ArrayList<Byte>(encoded.length)
        var index = 0
        while (index < encoded.length) {
            val char = encoded[index]
            if (char != '%') {
                appendUtf8(bytes, char)
                index++
                continue
            }
            if (index + 2 >= encoded.length) {
                throw unsafe(archivePath, reference, documentPath, "bad-percent-encoding")
            }
            val high = hexValue(encoded[index + 1])
            val low = hexValue(encoded[index + 2])
            if (high < 0 || low < 0) {
                throw unsafe(archivePath, reference, documentPath, "bad-percent-encoding")
            }
            bytes.add(((high shl 4) or low).toByte())
            index += 3
        }
        val decoded = decodeUtf8Strict(archivePath, reference, documentPath, bytes)
        return decoded
    }

    private fun appendUtf8(target: MutableList<Byte>, char: Char) {
        val code = char.code
        when {
            code < 0x80 -> target.add(code.toByte())
            code < 0x800 -> {
                target.add((0xC0 or (code shr 6)).toByte())
                target.add((0x80 or (code and 0x3F)).toByte())
            }
            char.isHighSurrogate() -> {
                // Lone surrogate in a path is malformed; encode its replacement bytes so decoding
                // fails in decodeUtf8Strict rather than silently dropping data.
                target.add(0xEF.toByte())
                target.add(0xBF.toByte())
                target.add(0xBD.toByte())
            }
            else -> {
                target.add((0xE0 or (code shr 12)).toByte())
                target.add((0x80 or ((code shr 6) and 0x3F)).toByte())
                target.add((0x80 or (code and 0x3F)).toByte())
            }
        }
    }

    private fun decodeUtf8Strict(
        archivePath: String,
        reference: String,
        documentPath: String?,
        bytes: List<Byte>,
    ): String {
        val builder = StringBuilder(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val first = bytes[index].toInt() and 0xFF
            val length = when {
                first < 0x80 -> 1
                first in 0xC2..0xDF -> 2
                first in 0xE0..0xEF -> 3
                first in 0xF0..0xF4 -> 4
                else -> 0
            }
            if (length == 0 || index + length > bytes.size) {
                throw unsafe(archivePath, reference, documentPath, "bad-percent-encoding")
            }
            var codePoint = when (length) {
                1 -> first
                2 -> first and 0x1F
                3 -> first and 0x0F
                else -> first and 0x07
            }
            for (offset in 1 until length) {
                val next = bytes[index + offset].toInt() and 0xFF
                if (next and 0xC0 != 0x80) {
                    throw unsafe(archivePath, reference, documentPath, "bad-percent-encoding")
                }
                codePoint = (codePoint shl 6) or (next and 0x3F)
            }
            val valid = when (length) {
                1 -> true
                2 -> codePoint >= 0x80
                3 -> codePoint >= 0x800 && codePoint !in 0xD800..0xDFFF
                else -> codePoint in 0x10000..0x10FFFF
            }
            if (!valid) {
                throw unsafe(archivePath, reference, documentPath, "bad-percent-encoding")
            }
            appendCodePoint(builder, codePoint)
            index += length
        }
        return builder.toString()
    }

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    /**
     * Normalizes an `encryption.xml` CipherReference URI without rejecting absolute forms.
     *
     * EPUB encryption URIs are archive-root relative in practice, but some tools write them with a
     * leading `/`, `./` or a redundant `//`. Those spellings must still map to the same archive entry,
     * otherwise a legitimately encrypted file would be treated as plaintext. Only a URI that cannot
     * name an in-archive entry (remote, escape above root) returns `null`; the caller then treats the
     * encryption entry as unknown and fails closed at read time rather than silently ignoring it.
     */
    fun normalizeEncryptionUri(uri: String): String? {
        val raw = uri.trim()
        if (raw.isEmpty()) return null
        if (isRemote(raw)) return null
        // Treat any leading slashes as archive-root relative, per EPUB's CipherReference convention.
        val withoutLeading = raw.trimStart('/')
        if (withoutLeading.isEmpty()) return null
        return try {
            resolveStructural("", null, withoutLeading)
        } catch (_: NovelImportException.UnsafeReference) {
            null
        }
    }

    /** `true` for C0/C1 control characters and DEL, which never belong in a resource path. */
    private fun Char.isControlOrDel(): Boolean = this.code < 0x20 || this.code == 0x7F

    private fun appendCodePoint(builder: StringBuilder, codePoint: Int) {
        if (codePoint <= 0xFFFF) {
            builder.append(codePoint.toChar())
        } else {
            val value = codePoint - 0x10000
            builder.append((0xD800 + (value shr 10)).toChar())
            builder.append((0xDC00 + (value and 0x3FF)).toChar())
        }
    }

    private fun unsafe(
        archivePath: String,
        reference: String,
        documentPath: String?,
        reason: String,
    ): NovelImportException.UnsafeReference =
        NovelImportException.UnsafeReference(
            path = archivePath,
            reference = reference,
            document = documentPath ?: "",
            reason = reason,
        )
}
