package dev.reader.media.novel

/**
 * Text encoding of the bytes read from a local novel.
 *
 * Only the encodings the importer understands are listed; detecting anything else is reported as
 * [NovelImportException.Encoding] or [NovelImportException.UnsupportedFormat].
 */
enum class TextEncoding(val displayName: String) {
    UTF_8("UTF-8"),
    UTF_16_LE("UTF-16LE"),
    UTF_16_BE("UTF-16BE"),
}
