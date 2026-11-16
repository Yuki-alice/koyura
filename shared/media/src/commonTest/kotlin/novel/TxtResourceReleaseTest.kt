package app.koyura.media.novel

import app.koyura.media.fixtureRoot
import app.koyura.media.novel.txt.openTxtNovel
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves the importer closes the underlying file source on every path: success, encoding failure,
 * unsupported format, empty file and read failure.
 *
 * The tracking double delegates all metadata work to [FileSystem.SYSTEM] and only wraps `source`,
 * so the fixtures are still the real checked-in bytes.
 */
class TxtResourceReleaseTest {

    private fun fixture(name: String): Path = fixtureRoot().toPath() / "txt" / name

    @Test
    fun closesSourceAfterSuccessfulOpen() {
        val fs = TrackingFileSystem()
        openTxtNovel(fs, fixture("sample-utf8.txt")).close()
        assertEquals(1, fs.opened)
        assertEquals(1, fs.closed)
        assertEquals(0, fs.stillOpen)
    }

    @Test
    fun closesSourceWhenDecodingFails() {
        val fs = TrackingFileSystem()
        assertFailsWith<NovelImportException.Encoding> { openTxtNovel(fs, fixture("invalid-utf8.txt")) }
        assertEquals(1, fs.opened)
        assertEquals(1, fs.closed)
        assertEquals(0, fs.stillOpen)
    }

    @Test
    fun closesSourceWhenFormatIsUnsupported() {
        val fs = TrackingFileSystem()
        assertFailsWith<NovelImportException.UnsupportedFormat> {
            openTxtNovel(fs, fixture("unsupported-format.txt"))
        }
        // The source must actually have been opened and closed, not skipped entirely.
        assertEquals(1, fs.opened)
        assertEquals(1, fs.closed)
        assertEquals(0, fs.stillOpen)
    }

    @Test
    fun closesSourceWhenFileIsEmpty() {
        val fs = TrackingFileSystem()
        assertFailsWith<NovelImportException.Empty> { openTxtNovel(fs, fixture("empty.txt")) }
        assertEquals(1, fs.opened)
        assertEquals(1, fs.closed)
        assertEquals(0, fs.stillOpen)
    }

    @Test
    fun closesSourceWhenReadingFails() {
        val fs = TrackingFileSystem(failReads = true)
        val error = assertFailsWith<NovelImportException.Io> {
            openTxtNovel(fs, fixture("sample-utf8.txt"))
        }
        assertEquals("read", error.phase)
        assertEquals(1, fs.opened)
        assertEquals(1, fs.closed)
        assertEquals(0, fs.stillOpen)
    }

    @Test
    fun closesSourceWhenOpeningFails() {
        val fs = TrackingFileSystem(failOpens = true)
        val error = assertFailsWith<NovelImportException.Io> {
            openTxtNovel(fs, fixture("sample-utf8.txt"))
        }
        assertEquals("open", error.phase)
        assertEquals(0, fs.opened)
    }
}

/**
 * Direct decoder checks for malformed byte sequences that have no fixture.
 *
 * These complement the fixture-based tests: they pin down which malformed shapes are rejected so a
 * future decoder rewrite cannot silently start replacing characters with U+FFFD.
 */
class TextDecoderEdgeCaseTest {

    private val path = "memory.txt"

    @Test
    fun rejectsOverlongUtf8Encoding() {
        val error = assertFailsWith<NovelImportException.Encoding> {
            TextDecoder.decode(path, bytes(0xC0, 0xAF))
        }
        assertEquals("UTF-8", error.encoding)
        assertEquals(0L, error.byteOffset)
    }

    @Test
    fun rejectsTruncatedUtf8SequenceAtEndOfFile() {
        val error = assertFailsWith<NovelImportException.Encoding> {
            TextDecoder.decode(path, bytes(0xE4, 0xB8))
        }
        assertEquals(0L, error.byteOffset)
        assertEquals(1, error.line)
    }

    @Test
    fun rejectsLoneContinuationByte() {
        val bytes = "ok\n".encodeToByteArray() + bytes(0x80)
        val error = assertFailsWith<NovelImportException.Encoding> {
            TextDecoder.decode(path, bytes)
        }
        assertEquals(3L, error.byteOffset)
        assertEquals(2, error.line)
    }

    @Test
    fun rejectsUtf16WithOddByteCountAtTheTrailingByteAndCurrentLine() {
        // BOM + "a\n" + "b\n" (each 2 bytes) + one stray byte = 3 lines worth, then a lone byte.
        val text = "a\nb\n"
        val payload = ByteArray(text.length * 2 + 1)
        text.forEachIndexed { index, char ->
            payload[index * 2] = (char.code and 0xFF).toByte()
            payload[index * 2 + 1] = ((char.code shr 8) and 0xFF).toByte()
        }
        payload[payload.size - 1] = 0x41
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + payload
        val error = assertFailsWith<NovelImportException.Encoding> { TextDecoder.decode(path, bytes) }
        assertEquals("UTF-16LE", error.encoding)
        // Byte offset points at the lone trailing byte, not one byte before it.
        assertEquals(bytes.size.toLong() - 1, error.byteOffset)
        // The failure is on the line reached after "a\n" and "b\n", not line 1.
        assertEquals(3, error.line)
    }

    @Test
    fun rejectsUtf16LoneHighSurrogate() {
        // BOM + U+D842 without a low surrogate.
        val bytes = bytes(0xFE, 0xFF, 0xD8, 0x42)
        val error = assertFailsWith<NovelImportException.Encoding> {
            TextDecoder.decode(path, bytes)
        }
        assertEquals("UTF-16BE", error.encoding)
        assertEquals(2L, error.byteOffset)
    }

    @Test
    fun decodesSupplementaryPlaneCodePointFromUtf8Bytes() {
        // F0 A0 AE B7 is U+20BB7 (𠮷).
        val text = TextDecoder.decode(path, bytes(0xF0, 0xA0, 0xAE, 0xB7)).text
        assertEquals("\uD842\uDFB7", text)
        assertEquals(0x20BB7, text.codePointAt(0))
    }

    @Test
    fun bomlessAsciiUtf16IsRejectedAsUnsupportedInsteadOfDecodedAsGarbage() {
        // ASCII in UTF-16LE puts a NUL in every odd byte, which is unambiguous.
        val text = "Chapter one\nHe opened the door.\n"
        val utf16NoBom = ByteArray(text.length * 2)
        text.forEachIndexed { index, char ->
            utf16NoBom[index * 2] = (char.code and 0xFF).toByte()
            utf16NoBom[index * 2 + 1] = ((char.code shr 8) and 0xFF).toByte()
        }
        val error = assertFailsWith<NovelImportException.UnsupportedFormat> {
            TextDecoder.decode(path, utf16NoBom)
        }
        assertEquals("utf-16-without-bom", error.format)
    }

    @Test
    fun bomlessCjkUtf16IsReportedAsAnEncodingErrorBecauseItIsOutOfScope() {
        // Chinese in UTF-16 has few NUL bytes, so it cannot be told apart from arbitrary binary.
        // Only BOM-marked UTF-16 is supported; anything else must fail loudly as an encoding error,
        // never decode into mojibake.
        val text = "第一章 夜雨\n他推开门。\n"
        val utf16NoBom = ByteArray(text.length * 2)
        text.forEachIndexed { index, char ->
            utf16NoBom[index * 2] = (char.code and 0xFF).toByte()
            utf16NoBom[index * 2 + 1] = ((char.code shr 8) and 0xFF).toByte()
        }
        val error = assertFailsWith<NovelImportException.Encoding> {
            TextDecoder.decode(path, utf16NoBom)
        }
        assertEquals("UTF-8", error.encoding)
    }

    @Test
    fun emptyBytesAreEmptyException() {
        val error = assertFailsWith<NovelImportException.Empty> { TextDecoder.decode(path, ByteArray(0)) }
        assertEquals(0L, error.byteSize)
    }

    @Test
    fun gzipAndPdfAreUnsupportedFormats() {
        assertEquals(
            "gzip",
            assertFailsWith<NovelImportException.UnsupportedFormat> {
                TextDecoder.decode(path, bytes(0x1F, 0x8B, 0x08, 0x00))
            }.format,
        )
        assertEquals(
            "pdf",
            assertFailsWith<NovelImportException.UnsupportedFormat> {
                TextDecoder.decode(path, "%PDF-1.7".encodeToByteArray())
            }.format,
        )
    }
}

/** Builds a [ByteArray] from integer literals so test bytes stay readable across platforms. */
private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

/** Returns the Unicode code point starting at [index] (surrogate pairs combined). */
private fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (!high.isHighSurrogate() || index + 1 >= length) return high.code
    val low = this[index + 1]
    if (!low.isLowSurrogate()) return high.code
    return 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
}

/**
 * Delegating filesystem that counts source lifecycles and can inject open/read failures.
 *
 * [stillOpen] is the leak detector: it must be zero after every call to [openTxtNovel].
 */
private class TrackingFileSystem(
    private val failReads: Boolean = false,
    private val failOpens: Boolean = false,
) : FileSystem() {
    private val delegate = FileSystem.SYSTEM
    private var openCount = 0
    private var closeCount = 0

    val opened: Int get() = openCount
    val closed: Int get() = closeCount
    val stillOpen: Int get() = openCount - closeCount

    override fun canonicalize(path: Path): Path = delegate.canonicalize(path)
    override fun metadataOrNull(path: Path) = delegate.metadataOrNull(path)
    override fun list(dir: Path): List<Path> = delegate.list(dir)
    override fun listOrNull(dir: Path): List<Path>? = delegate.listOrNull(dir)
    override fun openReadOnly(file: Path) = delegate.openReadOnly(file)
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean) =
        delegate.openReadWrite(file, mustCreate, mustExist)

    override fun source(file: Path): Source {
        if (failOpens) throw IOException("synthetic open failure")
        val inner = delegate.source(file)
        openCount++
        return object : Source {
            private var closed = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (failReads) throw IOException("synthetic read failure")
                return inner.read(sink, byteCount)
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                if (closed) return
                closed = true
                closeCount++
                inner.close()
            }
        }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink = delegate.sink(file, mustCreate)
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = delegate.appendingSink(file, mustExist)
    override fun createDirectory(dir: Path, mustCreate: Boolean) = delegate.createDirectory(dir, mustCreate)
    override fun atomicMove(source: Path, target: Path) = delegate.atomicMove(source, target)
    override fun delete(path: Path, mustExist: Boolean) = delegate.delete(path, mustExist)
    override fun createSymlink(source: Path, target: Path) = delegate.createSymlink(source, target)
    override fun toString(): String = "TrackingFileSystem(failReads=$failReads, failOpens=$failOpens)"
}
