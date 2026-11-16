package dev.reader.media.novel

/**
 * Safety caps applied while reading a local novel.
 *
 * Defaults are deliberately generous for real books but still bound worst-case memory and time, so a
 * malicious or accidentally huge file cannot exhaust the device. Every cap is independently
 * configurable; exceeding one throws [NovelImportException.LimitExceeded].
 *
 * - [maxChapterBytes] — largest single chapter document accepted, measured in **uncompressed** bytes.
 * - [maxResourceBytes] — largest single image/cover read through `readResource`.
 * - [maxChapterCount] — maximum number of readable chapters (spine documents) accepted.
 * - [maxMetadataBytes] — largest container/OPF/nav/encryption XML accepted. Metadata is read at open,
 *   so this is the cap that stops a "zip bomb" from exhausting memory before any chapter is touched.
 * - [maxCachedChapters] — how many parsed chapters stay in memory. Browsing a whole book does not
 *   accumulate its text.
 *
 * These caps bound the content this importer materializes, not the raw archive size. The ZIP central
 * directory is indexed separately with its own fixed bounds so a hostile directory cannot allocate
 * arbitrary memory either.
 */
data class LocalNovelLimits(
    val maxChapterBytes: Long = 8L * 1024 * 1024,
    val maxResourceBytes: Long = 32L * 1024 * 1024,
    val maxChapterCount: Int = 5000,
    val maxMetadataBytes: Long = 4L * 1024 * 1024,
    val maxCachedChapters: Int = 8,
) {
    init {
        require(maxChapterBytes > 0) { "maxChapterBytes must be positive" }
        require(maxResourceBytes > 0) { "maxResourceBytes must be positive" }
        require(maxChapterCount > 0) { "maxChapterCount must be positive" }
        require(maxMetadataBytes > 0) { "maxMetadataBytes must be positive" }
        require(maxCachedChapters > 0) { "maxCachedChapters must be positive" }
    }

    companion object {
        /** Default limits for production use. */
        val DEFAULT: LocalNovelLimits = LocalNovelLimits()

        /** Disables each cap; useful in tests that need to prove a specific behavior. */
        val UNLIMITED: LocalNovelLimits = LocalNovelLimits(
            maxChapterBytes = Long.MAX_VALUE,
            maxResourceBytes = Long.MAX_VALUE,
            maxChapterCount = Int.MAX_VALUE,
            maxMetadataBytes = Long.MAX_VALUE,
            maxCachedChapters = Int.MAX_VALUE,
        )
    }
}
