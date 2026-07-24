package com.vibetuned.to_reply.data.repo

import com.vibetuned.to_reply.data.db.PlayEntity
import com.vibetuned.to_reply.data.model.Play

/**
 * Delimiter for the persisted speaker list — the ASCII unit separator, which cannot occur in a
 * speaker name coming out of a JSON script. An old single-speaker value contains no separator
 * and decodes as a one-element list, which is why the multi-select change needed no migration.
 */
internal const val SPEAKERS_SEPARATOR = "\u001F"

internal fun encodeSpeakers(speakers: List<String>): String? =
    speakers.takeIf { it.isNotEmpty() }?.joinToString(SPEAKERS_SEPARATOR)

internal fun decodeSpeakers(raw: String?): List<String> =
    raw?.split(SPEAKERS_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

internal fun PlayEntity.toDomain() = Play(
    id = id,
    title = title,
    audioPath = audioPath,
    scriptPath = scriptPath,
    coverPath = coverPath,
    durationMs = durationMs,
    importedAt = importedAt,
    fileSize = fileSize,
    selectedSpeakers = decodeSpeakers(selectedSpeakers)
)
