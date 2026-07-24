package com.vibetuned.to_reply.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vibetuned.to_reply.ui.home.HomeScreen
import com.vibetuned.to_reply.ui.training.TrainingScreen

object TrainingRoute {
    const val PATTERN = "training?playId={playId}&autoPlay={autoPlay}"
    fun forPlay(playId: String, autoPlay: Boolean = false) =
        "training?playId=$playId&autoPlay=$autoPlay"
}

@Composable
fun ToReplyNavGraph(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.Start.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TopLevelDestination.Home.route) {
            HomeScreen(
                onOpenPlay = { playId ->
                    navController.navigate(TrainingRoute.forPlay(playId, autoPlay = true))
                }
            )
        }
        composable(
            route = TrainingRoute.PATTERN,
            arguments = listOf(
                navArgument("playId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("autoPlay") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            )
        ) { entry ->
            val playId = entry.arguments?.getString("playId")
            if (playId != null) {
                TrainingScreen(
                    playId = playId,
                    autoPlay = entry.arguments?.getBoolean("autoPlay") ?: false,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
