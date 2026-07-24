package com.vibetuned.to_reply.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.vibetuned.to_reply.ToReplyApplication
import com.vibetuned.to_reply.di.AppContainer

@Composable
@ReadOnlyComposable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as ToReplyApplication).container
}
