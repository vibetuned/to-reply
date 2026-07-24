package com.vibetuned.to_reply.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An imported play: a fully-voiced .m4b recording plus its timed play.json script, both copied
 * into private storage at import ([audioPath]/[scriptPath] are absolute paths under filesDir).
 * The script's timed entries deliberately do NOT live in Room: every consumer needs the whole
 * script in order, so it's parsed from [scriptPath] on demand and cached in PlayRepository —
 * this keeps the entry schema soft (no migration when the script format grows a field).
 */
@Entity(tableName = "plays")
data class PlayEntity(
    @PrimaryKey val id: String,
    val title: String,
    val audioPath: String,
    val scriptPath: String,
    val coverPath: String?,
    val durationMs: Long,
    val importedAt: Long,
    val fileSize: Long,
    /**
     * The characters the user rehearses in this play, as raw speaker strings joined with the
     * ASCII unit separator (U+001F — can never appear in a speaker name), or null when none are
     * picked yet. The column keeps its original single-value name deliberately: an old
     * single-speaker value is just a valid one-element list, so no migration was needed when
     * multi-select arrived. Updated via a targeted UPDATE so it never races a full-row upsert.
     */
    @ColumnInfo(name = "selectedSpeaker")
    val selectedSpeakers: String? = null
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val playId: String,
    val positionMs: Long,
    val updatedAt: Long
)
