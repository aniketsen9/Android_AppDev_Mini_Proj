package com.aniket.fitnesslibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    val repo = (LocalContext.current.applicationContext as App).repo
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Tab("exercises", "Exercises", Icons.Default.Home),
        Tab("plans", "Plans", Icons.AutoMirrored.Filled.List),
        Tab("history", "Completed", Icons.Default.Check),
    )
    Scaffold(bottomBar = {
        if (route in tabs.map { it.route }) NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = route == t.route,
                    onClick = {
                        nav.navigate(t.route) {
                            popUpTo("exercises") { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, "exercises", Modifier.padding(pad)) {
            composable("exercises") { ExerciseListScreen(repo) { nav.navigate("exercise/$it") } }
            composable("exercise/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                ExerciseDetailScreen(repo, it.arguments!!.getInt("id")) { nav.popBackStack() }
            }
            composable("plans") { PlansScreen(repo) { nav.navigate("plan/$it") } }
            composable("plan/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                PlanDetailScreen(repo, it.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("history") { HistoryScreen(repo) }
        }
    }
}
