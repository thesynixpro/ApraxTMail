package dev.aprax.tmaii.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.RefreshInterval
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.feature.common.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val refresh by viewModel.refreshInterval.collectAsStateWithLifecycle()
    val defaultProvider by viewModel.defaultProvider.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val autoRefresh by viewModel.autoRefreshEnabled.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var baseUrlInput by remember { mutableStateOf(state.baseUrl) }

    LaunchedEffect(state.baseUrl) { baseUrlInput = state.baseUrl }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Appearance
            SectionTitle("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            // Sync
            SectionTitle("Inbox sync")
            SettingSwitch(
                title = "Auto refresh",
                subtitle = "Poll inbox automatically while open",
                checked = autoRefresh,
                onCheckedChange = viewModel::setAutoRefresh,
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            )
            Text("Refresh interval", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefreshInterval.entries.forEach { interval ->
                    FilterChip(
                        selected = refresh == interval,
                        onClick = { viewModel.setRefreshInterval(interval) },
                        label = { Text(interval.label.replace(" seconds", "s").replace(" minute", "m"), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Text(
                "Minimum 15s is enforced to respect provider quotas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingSwitch(
                title = "Notifications",
                subtitle = "Notify when new mail or OTP arrives",
                checked = notifications,
                onCheckedChange = viewModel::setNotifications,
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            )

            // Defaults
            SectionTitle("Defaults")
            Text("Default provider", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("tmailor", "freecustom").forEach { p ->
                    FilterChip(
                        selected = defaultProvider == p,
                        onClick = { viewModel.setDefaultProvider(p) },
                        label = { Text(p.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            // Account
            SectionTitle("Account")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAccount),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.email ?: "Guest session", style = MaterialTheme.typography.titleSmall)
                        Text(
                            state.role?.replaceFirstChar { it.uppercase() } ?: "Continue as guest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Manage", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }

            // Backend
            SectionTitle("Backend connection")
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                label = { Text("Backend base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("HTTPS URL of your Aprax backend") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.setBaseUrl(baseUrlInput) }) { Text("Save URL") }
                OutlinedButton(onClick = viewModel::checkBackend, enabled = !state.checkingBackend) {
                    if (state.checkingBackend) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test connection")
                    }
                }
            }
            state.health?.let { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = if (h.status == "ok") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        label = "Backend ${h.status} · DB ${h.database} · v${h.version}",
                    )
                }
                Text(
                    "Providers: " + h.providers.joinToString { "${it.id}${if (it.configured) "✓" else "✗"}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.backendError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Data
            SectionTitle("Data")
            OutlinedButton(
                onClick = viewModel::clearHistory,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear local history") }

            if (state.role != null && state.role != "GUEST") {
                OutlinedButton(
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }

            // About
            SectionTitle("About")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Aprax TMaii v1.0.0 · Temporary email platform",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Privacy: messages are stored only as long as the provider retains them " +
                    "(Tmailor 24h; FreeCustom per plan). No email content is sold or shared. " +
                    "Provider API keys never leave the backend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
