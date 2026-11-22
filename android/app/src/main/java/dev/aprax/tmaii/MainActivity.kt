package dev.aprax.tmaii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.Accent
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.ui.theme.ApraxTheme
import dev.aprax.tmaii.feature.account.AccountScreen
import dev.aprax.tmaii.feature.detail.MessageDetailScreen
import dev.aprax.tmaii.feature.domain.AddDomainScreen
import dev.aprax.tmaii.feature.domain.DomainVerifyScreen
import dev.aprax.tmaii.feature.domain.DomainsScreen
import dev.aprax.tmaii.feature.generator.GeneratorScreen
import dev.aprax.tmaii.feature.home.HomeScreen
import dev.aprax.tmaii.feature.inbox.InboxScreen
import dev.aprax.tmaii.feature.navigation.Routes
import dev.aprax.tmaii.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsDataStore,
) : ViewModel() {

    val themeMode = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val accent = settings.accent
        .stateIn(viewModelScope, SharingStarted.Eagerly, Accent.VIOLET)
    val signedIn = settings.authToken
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val ready = settings.authToken
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val accent by vm.accent.collectAsStateWithLifecycle()
            val signedIn by vm.signedIn.collectAsStateWithLifecycle()
            val ready by vm.ready.collectAsStateWithLifecycle()

            ApraxTheme(mode = themeMode, accent = accent) {
                ApraxRoot(
                    ready = ready,
                    signedIn = signedIn,
                )
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.INBOX, "Inbox", Icons.Outlined.Inbox),
    Tab(Routes.DOMAINS, "Domains", Icons.Outlined.Domain),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
private fun ApraxRoot(ready: Boolean, signedIn: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showTabs = currentRoute in tabs.map { it.route }

    if (!ready) return

    val startDestination = if (signedIn) Routes.HOME else Routes.ACCOUNT

    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = showTabs, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onOpenGenerator = { navController.navigate(Routes.GENERATOR) },
                    onOpenDomains = { navController.navigate(Routes.DOMAINS) },
                )
            }
            composable(Routes.INBOX) {
                InboxScreen(
                    onOpenMessage = { id -> navController.navigate(Routes.messageDetail(id)) },
                )
            }
            composable(Routes.DOMAINS) {
                DomainsScreen(
                    onAddDomain = { navController.navigate(Routes.ADD_DOMAIN) },
                    onOpenDomain = { id -> navController.navigate(Routes.domainVerify(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onSignedOut = {
                        navController.navigate(Routes.ACCOUNT) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    onSignedIn = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ACCOUNT) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.GENERATOR) {
                GeneratorScreen(
                    onBack = { navController.popBackStack() },
                    onAddedDomain = {
                        navController.navigate(Routes.ADD_DOMAIN)
                    },
                    onOpenInbox = {
                        navController.navigate(Routes.INBOX) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }
            composable(Routes.ADD_DOMAIN) {
                AddDomainScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { domainId ->
                        navController.navigate(Routes.domainVerify(domainId)) {
                            popUpTo(Routes.DOMAINS)
                        }
                    },
                )
            }
            composable(
                route = Routes.DOMAIN_VERIFY,
                arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
            ) {
                DomainVerifyScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.MESSAGE_DETAIL,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
            ) {
                MessageDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
