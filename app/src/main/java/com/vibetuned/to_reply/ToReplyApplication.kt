package com.vibetuned.to_reply

import android.app.Application
import com.vibetuned.to_reply.di.AppContainer

class ToReplyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Off the main thread: an interrupted zip import can leave a whole recording behind.
        Thread({ container.playRepository.cleanUpTemporaryFiles() }, "temp-cleanup").start()
    }
}
