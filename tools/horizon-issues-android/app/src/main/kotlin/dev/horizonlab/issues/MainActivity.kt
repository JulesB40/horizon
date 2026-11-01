package dev.horizonlab.issues

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.horizonlab.issues.ui.DetailUi
import dev.horizonlab.issues.ui.IssueDetailScreen
import dev.horizonlab.issues.ui.IssueListScreen
import dev.horizonlab.issues.ui.IssuesViewModel
import dev.horizonlab.issues.ui.SettingsScreen
import dev.horizonlab.issues.ui.components.SavedActions
import dev.horizonlab.issues.ui.theme.HorizonIssuesTheme
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: IssuesViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            HorizonIssuesTheme(dark) { App(vm) }
        }
    }
}

private const val DURATION = 280

@Composable
private fun App(vm: IssuesViewModel) {
    val nav = rememberNavController()
    val list by vm.list.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rateLimit by vm.rateLimit.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val actions = remember(vm) {
        SavedActions(
            pin = vm::togglePin,
            toggle = vm::setInGroup,
            create = { name, number -> vm.createGroup(name, number) },
            rename = vm::renameGroup,
            delete = vm::deleteGroup,
            show = vm::setGroup,
        )
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    NavHost(
        navController = nav,
        startDestination = "list",
        modifier = Modifier.fillMaxSize().background(Theme.palette.screen),
        enterTransition = { slideInHorizontally(tween(DURATION)) { it / 4 } + fadeIn(tween(DURATION)) },
        exitTransition = { slideOutHorizontally(tween(DURATION)) { -it / 8 } + fadeOut(tween(DURATION / 2)) },
        popEnterTransition = { slideInHorizontally(tween(DURATION)) { -it / 8 } + fadeIn(tween(DURATION)) },
        popExitTransition = { slideOutHorizontally(tween(DURATION)) { it / 4 } + fadeOut(tween(DURATION / 2)) },
    ) {
        composable("list") {
            IssueListScreen(
                ui = list,
                snackbar = snackbar,
                onRefresh = { vm.refresh(fresh = true) },
                onScope = vm::setScope,
                onFilter = vm::setFilter,
                onSort = vm::setSort,
                onQuery = vm::setQuery,
                onLabel = vm::setLabel,
                onOpen = { nav.navigate("issue/$it") },
                onSettings = { nav.navigate("settings") },
                actions = actions,
            )
        }
        composable("issue/{number}", arguments = listOf(navArgument("number") { type = NavType.IntType })) { entry ->
            val number = entry.arguments?.getInt("number") ?: return@composable
            LaunchedEffect(number) { vm.loadDetail(number) }
            IssueDetailScreen(
                number = number,
                repo = list.repo,
                listCopy = list.lists.values.asSequence().flatten().firstOrNull { it.number == number },
                state = details[number] ?: DetailUi(loading = true),
                canComment = settings.token.isNotBlank(),
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onRefresh = { vm.loadDetail(number) },
                onClaim = { vm.claim(number) },
                saved = list.saved,
                actions = actions,
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                rateLimit = rateLimit,
                onBack = { nav.popBackStack() },
                onSave = vm::updateSettings,
            )
        }
    }
}
