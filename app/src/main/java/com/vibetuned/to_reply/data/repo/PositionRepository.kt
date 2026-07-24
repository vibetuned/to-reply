package com.vibetuned.to_reply.data.repo

import com.vibetuned.to_reply.data.db.PositionDao
import com.vibetuned.to_reply.data.db.PositionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PositionRepository(
    private val positionDao: PositionDao
) {
    fun observePositionMs(playId: String): Flow<Long?> =
        positionDao.observePositionMs(playId)

    /** playId -> saved position (ms), for every play that has one. */
    fun observeAllPositions(): Flow<Map<String, Long>> =
        positionDao.observeAll().map { positions -> positions.associate { it.playId to it.positionMs } }

    suspend fun get(playId: String): Long? =
        positionDao.get(playId)?.positionMs

    /** Play the user most recently had playback in (and that still exists), or null. */
    suspend fun lastPlayedPlayId(): String? =
        positionDao.mostRecentExistingPlayId()

    suspend fun save(playId: String, positionMs: Long) {
        positionDao.upsert(
            PositionEntity(
                playId = playId,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear(playId: String) {
        positionDao.delete(playId)
    }
}
