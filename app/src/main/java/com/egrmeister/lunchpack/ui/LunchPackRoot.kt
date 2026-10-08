@file:OptIn(ExperimentalLayoutApi::class)

package com.egrmeister.lunchpack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.R
import com.egrmeister.lunchpack.ui.history.HistoryDetailScreen
import com.egrmeister.lunchpack.ui.history.HistoryDetailViewModel
import com.egrmeister.lunchpack.ui.history.HistoryScreen
import com.egrmeister.lunchpack.ui.history.HistoryViewModel
import com.egrmeister.lunchpack.ui.home.HomeScreen
import com.egrmeister.lunchpack.ui.home.HomeViewModel
import com.egrmeister.lunchpack.ui.packs.EditorScreen
import com.egrmeister.lunchpack.ui.packs.EditorViewModel
import com.egrmeister.lunchpack.ui.packs.PacksScreen
import com.egrmeister.lunchpack.ui.packs.PacksViewModel
import com.egrmeister.lunchpack.ui.packs.PickerScreen
import com.egrmeister.lunchpack.ui.packs.PickerViewModel
import com.egrmeister.lunchpack.ui.review.ReviewScreen
import com.egrmeister.lunchpack.ui.review.ReviewViewModel
import com.egrmeister.lunchpack.ui.settings.PrivacyScreen
import com.egrmeister.lunchpack.ui.settings.SettingsScreen
import com.egrmeister.lunchpack.ui.settings.SettingsViewModel
import com.egrmeister.lunchpack.ui.theme.LocalReducedMotion
import com.egrmeister.lunchpack.ui.week.WeekScreen
import com.egrmeister.lunchpack.ui.week.WeekViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** A tap on the reminder notification: open the planned pack for [date]. */
data class OpenPlanRequest(val date: LocalDate, val nonce: Long)

private object Routes {
    const val HOME = "home"
    const val PACKS = "packs"
    const val WEEK = "week"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?templateId={templateId}"
    const val PICKER = "picker?date={date}"
    const val REVIEW = "review/{sessionId}"
    const val HISTORY_DETAIL = "history/{entryId}"
    const val PRIVACY = "privacy"

    fun editor(id: Long?) = "editor?templateId=${id ?: -1L}"
    fun picker(date: LocalDate?) = if (date == null) "picker" else "picker?date=$date"
    fun review(id: Long) = "review/$id"
    fun historyDetail(id: Long) = "history/$id"
}

private data class TopDestination(val route: String, val label: String)

private val topDestinations = listOf(
    TopDestination(Routes.HOME, "Box"),
    TopDestination(Routes.PACKS, "Packs"),
    TopDestination(Routes.WEEK, "Week"),
    TopDestination(Routes.HISTORY, "History"),
    TopDestination(Routes.SETTINGS, "Settings"),
)

@Composable
private fun TopIcon(route: String) {
    when (route) {
        Routes.HOME -> Icon(Icons.Filled.Home, contentDescription = null)
        Routes.PACKS -> Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        Routes.WEEK -> Icon(Icons.Filled.DateRange, contentDescription = null)
        Routes.HISTORY -> Icon(painterResource(R.drawable.ic_nav_history), contentDescription = null)
        else -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun LunchPackRoot(
    container: AppContainer,
    openPlanRequests: StateFlow<OpenPlanRequest?>,
    onOpenPlanHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showNav = currentRoute == null || topDestinations.any { it.route == currentRoute }
    val reducedMotion by container.settings.reducedMotionFlow.collectAsStateWithLifecycle(initialValue = false)
    val openRequest by openPlanRequests.collectAsStateWithLifecycle()

    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        val plan = container.repository.sessionForDateFlow(request.date).first()
        // If the plan was removed meanwhile, Box falls back to today's plan instead.
        container.settings.setSelectedSession(plan?.id)
        navController.navigateTop(Routes.HOME)
        onOpenPlanHandled()
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 600.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        if (showNav) {
                            NavigationRail {
                                topDestinations.forEach { dest ->
                                    NavigationRailItem(
                                        selected = currentRoute == dest.route,
                                        onClick = { navController.navigateTop(dest.route) },
                                        icon = { TopIcon(dest.route) },
                                        label = { Text(dest.label) },
                                    )
                                }
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .then(
                                    if (showNav) {
                                        Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            LunchNavHost(container, navController, Modifier.fillMaxSize())
                        }
                    }
                } else {
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (showNav) {
                                NavigationBar {
                                    topDestinations.forEach { dest ->
                                        NavigationBarItem(
                                            selected = currentRoute == dest.route,
                                            onClick = { navController.navigateTop(dest.route) },
                                            icon = { TopIcon(dest.route) },
                                            label = { Text(dest.label) },
                                        )
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        LunchNavHost(
                            container,
                            navController,
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .consumeWindowInsets(padding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LunchNavHost(container: AppContainer, navController: NavHostController, modifier: Modifier) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel { HomeViewModel(container) }
            HomeScreen(
                viewModel = vm,
                onReview = { navController.navigate(Routes.review(it)) },
                onChoosePack = { session -> navController.navigate(Routes.picker(session?.plannedDate)) },
                onOpenWeek = { navController.navigateTop(Routes.WEEK) },
            )
        }
        composable(Routes.PACKS) {
            val vm: PacksViewModel = viewModel { PacksViewModel(container) }
            PacksScreen(
                viewModel = vm,
                onEdit = { navController.navigate(Routes.editor(it)) },
                onPackNow = { navController.navigate(Routes.picker(null)) },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("templateId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("templateId")?.takeIf { it >= 0 }
            val vm: EditorViewModel = viewModel { EditorViewModel(container, id) }
            EditorScreen(viewModel = vm, onClose = { navController.popBackStack() })
        }
        composable(
            Routes.PICKER,
            arguments = listOf(
                navArgument("date") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val vm: PickerViewModel = viewModel { PickerViewModel(container, date) }
            PickerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onDone = { openedSessionId ->
                    if (openedSessionId != null) {
                        navController.navigateTop(Routes.HOME)
                    } else {
                        navController.popBackStack()
                    }
                },
                onCreatePack = { navController.navigate(Routes.editor(null)) },
            )
        }
        composable(Routes.WEEK) {
            val vm: WeekViewModel = viewModel { WeekViewModel(container) }
            WeekScreen(
                viewModel = vm,
                onChoosePack = { navController.navigate(Routes.picker(it)) },
                onOpenPack = { navController.navigateTop(Routes.HOME) },
            )
        }
        composable(Routes.REVIEW, arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("sessionId") ?: -1L
            val vm: ReviewViewModel = viewModel { ReviewViewModel(container, id) }
            ReviewScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
            HistoryScreen(viewModel = vm, onOpen = { navController.navigate(Routes.historyDetail(it)) })
        }
        composable(Routes.HISTORY_DETAIL, arguments = listOf(navArgument("entryId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("entryId") ?: -1L
            val vm: HistoryDetailViewModel = viewModel { HistoryDetailViewModel(container, id) }
            HistoryDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
            SettingsScreen(
                viewModel = vm,
                onManagePacks = { navController.navigateTop(Routes.PACKS) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}
