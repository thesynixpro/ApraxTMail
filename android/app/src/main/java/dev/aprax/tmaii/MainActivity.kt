package dev.aprax.tmaii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.ActiveMailboxStore
import dev.aprax.tmaii.core.ui.theme.ApraxTheme
import dev.aprax.tmaii.feature.account.AccountScreen
import dev.aprax.tmaii.feature.generator.GeneratorScreen
import dev.aprax.tmaii.feature.history.HistoryScreen
import dev.aprax.tmaii.feature.home.HomeScreen
import dev.aprax.tmaii.feature.inbox.InboxScreen
import dev.aprax.tmaii.feature.navigation.Routes
import dev.aprax.tmaii.feature.settings.SettingsScreen
import dev.aprax.tmaii.sync.InboxSyncWorker
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val settings: SettingsDataStore,
    val activeMailbox: ActiveMailboxStore,
) : ViewModel()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        InboxSyncWorker.schedule(applicationContext, intervalMinutes = 15)
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM,
            )
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> null
            }
            ApraxTheme(darkTheme = darkTheme ?: isSystemInDarkThemeDefault()) {
                ApraxRoot(vm)
            }
        }
    }
}

@Composable
private fun isSystemInDarkThemeDefault(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomTab(Routes.INBOX, R.string.nav_inbox, Icons.Filled.Email),
    BottomTab(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    BottomTab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
private fun ApraxRoot(vm: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val activeMailboxId by vm.activeMailbox.id.collectAsStateWithLifecycle()

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenGenerator = { navController.navigate(Routes.GENERATOR) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.INBOX) {
                InboxScreen(
                    activeMailboxId = activeMailboxId,
                    onOpenMessage = { messageId ->
                        val mailboxId = activeMailboxId
                        if (mailboxId != null) {
                            navController.navigate(Routes.messageDetail(mailboxId, messageId))
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onReopen = { mailboxId ->
                        vm.activeMailbox.set(mailboxId)
                        navController.navigate(Routes.INBOX) {
                            launchSingleTop = true
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.GENERATOR) {
                GeneratorScreen(
                    onBack = { navController.popBackStack() },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(
                route = Routes.MESSAGE_DETAIL,
                arguments = listOf(
                    navArgument("mailboxId") { type = NavType.StringType },
                    navArgument("messageId") { type = NavType.StringType },
                ),
            ) { entry ->
                val mailboxId = entry.arguments?.getString("mailboxId") ?: return@composable
                val messageId = entry.arguments?.getString("messageId") ?: return@composable
                MessageDetailRoute(
                    mailboxId = mailboxId,
                    messageId = messageId,
                    onBack = { navController.popBackStack() },
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

@Composable
private fun MessageDetailRoute(
    mailboxId: String,
    messageId: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    dev.aprax.tmaii.feature.detail.MessageDetailScreen(
        mailboxId = mailboxId,
        messageId = messageId,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}
