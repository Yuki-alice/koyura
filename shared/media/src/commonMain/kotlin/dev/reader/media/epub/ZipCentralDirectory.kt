package dev.reader.media.epub

import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * ZIP central-directory index used for integrity and size checks.
 *
 * Okio's `openZip` is the read path, but it exposes neither the stored CRC nor a way to distinguish
 * "entry truncated to a smaller declared size" from a genuinely short entry. Both matter for safety:
 *
 * - a hostile archive can declare a tiny uncompressed size so a shorter body is returned with no
 *   error, silently producing wrong text;
 * - a stored *or deflated* entry can be replaced in place, and Okio will happily return the bytes.
 *
 * This index reads the **exact** central-directory range named by the end-of-central-directory record
 * (including the ZIP64 variant), so every entry in a long book is indexed. It records each entry's
 * declared size and CRC so callers can verify what they read.
 *
 * A `null` result means the archive has no usable central directory. EPUB's OCF container format
 * requires one, so the importer treats that as a corrupted/unsupported archive rather than skipping
 * verification.
 */
internal class ZipCentralDirectory private constructor(
    private val entries: Map<String, Entry>,
) {
    /** One central-directory record. */
    data class Entry(
        val path: String,
        val compressionMethod: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
    )

    fun entry(path: String): Entry? = entries[path]

    val size: Int get() = entries.size

    companion object {
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50
        private const val ZIP64_EOCD_SIGNATURE = 0x06064b50
        private const val CENTRAL_SIGNATURE = 0x02014b50

        private const val EOCD_MIN_SIZE = 22
        private const val CENTRAL_HEADER_SIZE = 46
        private const val ZIP64_LOCATOR_SIZE = 20
        private const val ZIP64_EOCD_MIN_SIZE = 56

        /** Hard cap on indexed entries; a huge directory is a sign of a hostile archive. */
        const val DEFAULT_MAX_ENTRIES = 200_000

        /** Hard cap on the central directory's own size. */
        const val DEFAULT_MAX_CENTRAL_DIRECTORY_BYTES = 64L * 1024 * 1024

        /**
         * Reads the central directory of the archive at [archivePath] on [fileSystem].
         *
         * @return the index, or `null` when no central directory can be read (missing/malformed
         *   EOCD, out-of-bounds offsets, unsupported multi-disk layout).
         */
        fun read(
            fileSystem: FileSystem,
            archivePath: Path,
            maxEntries: Int = DEFAULT_MAX_ENTRIES,
            maxCentralDirectoryBytes: Long = DEFAULT_MAX_CENTRAL_DIRECTORY_BYTES,
        ): ZipCentralDirectory? {
            val size = try {
                fileSystem.metadataOrNull(archivePath)?.size ?: return null
            } catch (_: IOException) {
                return null
            }
            if (size < EOCD_MIN_SIZE) return null

            // Locate the EOCD by scanning only its maximum possible tail (comment <= 65535 bytes).
            val tailLength = minOf(size, 65_536L + EOCD_MIN_SIZE)
            val tailStart = size - tailLength
            val tail = try {
                fileSystem.read(archivePath) {
                    skip(tailStart)
                    readByteArray(tailLength)
                }
            } catch (_: IOException) {
                return null
            }
            val eocdInTail = findEocd(tail) ?: return null
            val eocdAbsolute = tailStart + eocdInTail

            val eocd = parseEocd(tail, eocdInTail) ?: return null
            var totalEntries = eocd.totalEntries
            var centralOffset = eocd.centralOffset
            var centralSize = eocd.centralSize

            if (eocd.needsZip64) {
                val zip64 = readZip64(fileSystem, archivePath, eocdAbsolute, size) ?: return null
                totalEntries = zip64.totalEntries
                centralOffset = zip64.centralOffset
                centralSize = zip64.centralSize
            }

            if (totalEntries <= 0 || totalEntries > maxEntries) return null
            if (centralSize <= 0 || centralSize > maxCentralDirectoryBytes) return null
            if (centralOffset < 0 || centralOffset + centralSize > size) return null

            // Read exactly the central directory. Navigating by the recorded offset is what makes the
            // index complete for long archives and immune to signature-like bytes in file data.
            val directory = try {
                fileSystem.read(archivePath) {
                    skip(centralOffset)
                    readByteArray(centralSize)
                }
            } catch (_: IOException) {
                return null
            }
            if (directory.size.toLong() != centralSize) return null

            val records = LinkedHashMap<String, Entry>()
            var cursor = 0
            for (index in 0 until totalEntries.toInt()) {
                if (cursor + CENTRAL_HEADER_SIZE > directory.size) return null
                if (readIntLe(directory, cursor) != CENTRAL_SIGNATURE) return null
                val method = readShortLe(directory, cursor + 10)
                val crc = readIntLe(directory, cursor + 16).toLong() and 0xFFFFFFFFL
                val compressed = readIntLe(directory, cursor + 20).toLong() and 0xFFFFFFFFL
                val uncompressed = readIntLe(directory, cursor + 24).toLong() and 0xFFFFFFFFL
                val nameLength = readShortLe(directory, cursor + 28)
                val extraLength = readShortLe(directory, cursor + 30)
                val commentLength = readShortLe(directory, cursor + 32)
                val nameStart = cursor + CENTRAL_HEADER_SIZE
                val nameEnd = nameStart + nameLength
                if (nameEnd > directory.size) return null
                // Entry names are UTF-8 in EPUB; decode as UTF-8 so the key matches Okio's path.
                val name = directory.decodeToString(nameStart, nameEnd)
                // Match Okio's resolution order: a later duplicate record wins.
                records[name] = Entry(
                    path = name,
                    compressionMethod = method,
                    crc32 = crc,
                    compressedSize = compressed,
                    uncompressedSize = uncompressed,
                )
                cursor = nameEnd + extraLength + commentLength
            }
            return ZipCentralDirectory(records)
        }

        private class Eocd(
            val totalEntries: Long,
            val centralOffset: Long,
            val centralSize: Long,
            val needsZip64: Boolean,
        )

        private class Zip64Eocd(
            val totalEntries: Long,
            val centralOffset: Long,
            val centralSize: Long,
        )

        private fun parseEocd(bytes: ByteArray, offset: Int): Eocd? {
            if (offset + EOCD_MIN_SIZE > bytes.size) return null
            val entriesOnDisk = readShortLe(bytes, offset + 8)
            val totalEntries = readShortLe(bytes, offset + 10).toLong() and 0xFFFFL
            val centralSize = readIntLe(bytes, offset + 12).toLong() and 0xFFFFFFFFL
            val centralOffset = readIntLe(bytes, offset + 16).toLong() and 0xFFFFFFFFL
            val needsZip64 = entriesOnDisk == 0xFFFF || totalEntries == 0xFFFFL ||
                centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL
            return Eocd(totalEntries, centralOffset, centralSize, needsZip64)
        }

        private fun readZip64(
            fileSystem: FileSystem,
            archivePath: Path,
            eocdAbsolute: Long,
            fileSize: Long,
        ): Zip64Eocd? {
            val locatorOffset = eocdAbsolute - ZIP64_LOCATOR_SIZE
            if (locatorOffset < 0) return null
            val locator = try {
                fileSystem.read(archivePath) {
                    skip(locatorOffset)
                    readByteArray(ZIP64_LOCATOR_SIZE.toLong())
                }
            } catch (_: IOException) {
                return null
            }
            if (readIntLe(locator, 0) != ZIP64_LOCATOR_SIGNATURE) return null
            val recordOffset = readLongLe(locator, 8)
            if (recordOffset < 0 || recordOffset + ZIP64_EOCD_MIN_SIZE > fileSize) return null
            val record = try {
                fileSystem.read(archivePath) {
                    skip(recordOffset)
                    readByteArray(ZIP64_EOCD_MIN_SIZE.toLong())
                }
            } catch (_: IOException) {
                return null
            }
            if (readIntLe(record, 0) != ZIP64_EOCD_SIGNATURE) return null
            // Fixed fields: total entries at +32, central size at +40, central offset at +48.
            return Zip64Eocd(
                totalEntries = readLongLe(record, 32),
                centralSize = readLongLe(record, 40),
                centralOffset = readLongLe(record, 48),
            )
        }

        private fun findEocd(tail: ByteArray): Int? {
            // Search backwards so a signature-like sequence inside file data is not preferred.
            for (index in tail.size - EOCD_MIN_SIZE downTo 0) {
                if (readIntLe(tail, index) == EOCD_SIGNATURE) return index
            }
            return null
        }

        private fun readShortLe(bytes: ByteArray, index: Int): Int =
            (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)

        private fun readIntLe(bytes: ByteArray, index: Int): Int =
            readShortLe(bytes, index) or (readShortLe(bytes, index + 2) shl 16)

        private fun readLongLe(bytes: ByteArray, index: Int): Long {
            val low = readIntLe(bytes, index).toLong() and 0xFFFFFFFFL
            val high = readIntLe(bytes, index + 4).toLong() and 0xFFFFFFFFL
            return low or (high shl 32)
        }
    }
}
