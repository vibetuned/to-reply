package com.vibetuned.to_reply.data.repo

/**
 * A play's title made safe as a file name on every system it might land on: characters that
 * Windows, macOS or Android reject or treat specially become spaces, runs of spaces collapse, and
 * the result is kept short enough for any file system. Ported from ln-reader's `shareFileName`.
 */
internal fun shareFileName(title: String): String {
    val cleaned = title
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
        .trim()
    val limited = if (cleaned.length > 120) cleaned.take(120).trimEnd() else cleaned
    return limited.ifEmpty { "Play" }
}
