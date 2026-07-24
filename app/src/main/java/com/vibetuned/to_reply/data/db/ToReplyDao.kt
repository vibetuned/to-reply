package com.vibetuned.to_reply.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayDao {
    @Query("SELECT * FROM plays ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<PlayEntity>>

    @Query("SELECT * FROM plays WHERE id = :id")
    suspend fun byId(id: String): PlayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(play: PlayEntity)

    /** [speakers] is the unit-separator-joined list, see [PlayEntity.selectedSpeakers]. */
    @Query("UPDATE plays SET selectedSpeaker = :speakers WHERE id = :id")
    suspend fun updateSelectedSpeakers(id: String, speakers: String?)

    @Query("DELETE FROM plays WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PositionDao {
    @Query("SELECT positionMs FROM positions WHERE playId = :playId")
    fun observePositionMs(playId: String): Flow<Long?>

    /** Every saved position. Used to show per-play progress bars across the home list. */
    @Query("SELECT * FROM positions")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE playId = :playId")
    suspend fun get(playId: String): PositionEntity?

    /**
     * Play id of the most recently saved position whose play still exists. Used on cold start to
     * reopen whatever the user was last rehearsing. The JOIN skips orphaned position rows that
     * may linger after a play is deleted.
     */
    @Query(
        "SELECT pos.playId FROM positions pos " +
            "INNER JOIN plays p ON p.id = pos.playId " +
            "ORDER BY pos.updatedAt DESC LIMIT 1"
    )
    suspend fun mostRecentExistingPlayId(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PositionEntity)

    @Query("DELETE FROM positions WHERE playId = :playId")
    suspend fun delete(playId: String)
}
