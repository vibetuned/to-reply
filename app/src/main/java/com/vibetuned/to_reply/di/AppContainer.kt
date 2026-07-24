package com.vibetuned.to_reply.di

import android.content.Context
import com.vibetuned.to_reply.data.db.ToReplyDatabase
import com.vibetuned.to_reply.data.prefs.TrainingPreferences
import com.vibetuned.to_reply.data.repo.PlayRepository
import com.vibetuned.to_reply.data.repo.PositionRepository
import com.vibetuned.to_reply.data.script.PlayScriptParser
import com.vibetuned.to_reply.m4b.M4bParser
import com.vibetuned.to_reply.player.PlayerHolder
import com.vibetuned.to_reply.player.TrainingController

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Whether this process has already auto-reopened the last-rehearsed play. Lives on the
     * process-scoped container so the restore fires once per fresh process — on a cold start and
     * after Android kills the backgrounded process — but not again across config changes
     * (rotation) within the same process. Read/written only from the main thread.
     */
    var lastPlayRestoreHandled = false

    val database: ToReplyDatabase by lazy {
        ToReplyDatabase.build(appContext)
    }

    val m4bParser: M4bParser by lazy { M4bParser() }

    val trainingPreferences: TrainingPreferences by lazy { TrainingPreferences(appContext) }

    val scriptParser: PlayScriptParser by lazy { PlayScriptParser() }

    val playRepository: PlayRepository by lazy {
        PlayRepository(
            context = appContext,
            database = database,
            playDao = database.playDao(),
            positionDao = database.positionDao(),
            m4bParser = m4bParser,
            scriptParser = scriptParser
        )
    }

    val positionRepository: PositionRepository by lazy {
        PositionRepository(database.positionDao())
    }

    val playerHolder: PlayerHolder by lazy {
        PlayerHolder(appContext)
    }

    val trainingController: TrainingController by lazy {
        TrainingController(playerHolder)
    }
}
