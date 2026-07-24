package com.vibetuned.to_reply.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlayEntity::class,
        PositionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ToReplyDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao
    abstract fun positionDao(): PositionDao

    companion object {
        fun build(context: Context): ToReplyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ToReplyDatabase::class.java,
                "to_reply.db"
            )
                // Safety net only — every real schema change must ship an incremental Migration
                // (see NEW_APP_SPEC.md §5.1). Never rely on this to deliver a schema change.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
