package com.vibetuned.to_reply.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations. Only Home for now — the enum (and the ln-reader bottom-nav pattern
 * that consumes it) is kept so adding a second tab later is a one-liner; MainActivity simply
 * doesn't render a NavigationBar while there's a single entry.
 */
enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Plays", Icons.Outlined.Home);

    companion object {
        val Start = Home
    }
}
