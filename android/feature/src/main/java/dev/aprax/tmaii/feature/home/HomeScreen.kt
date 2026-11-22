package dev.aprax.tmaii.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.GradientCard
import dev.aprax.tmaii.feature.common.LoadingState
import dev.aprax.tmaii.feature.common.StatusDot

@Composable
fun HomeScreen(
    onOpenInbox: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenDomains: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    when {
        state.loading -> LoadingState(label = "Loading dashboard...")
        state.error != null && state.domainCount == 0 && state.unread == 0 -> {
            ErrorState(message = state.error ?: "Something went wrong", onRetry = viewModel::refresh)
        }
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Welcome back", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.email ?: "Signed in",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
                StatusDot(
                    color = when (state.backendOk) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.outline
                    },
                    label = when (state.backendOk) {
                        true -> "Online"
                        false -> "Offline"
                        null -> "…"
                    },
                )
            }

            AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
                GradientCard {
                    Column {
                        Text("Unread mail", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                state.unread.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("messages", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = onOpenInbox) {
                            Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open inbox")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Domain,
                    value = state.domainCount.toString(),
                    label = "Domains",
                    sublabel = "${state.verifiedDomains} verified",
                    onClick = onOpenDomains,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Email,
                    value = "New",
                    label = "Address",
                    sublabel = "Generate one",
                    onClick = onOpenGenerator,
                )
            }

            state.lastAddress?.let { address ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your latest address", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(address, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy address")
                        }
                    }
                }
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenDomains, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add domain")
                }
                Button(onClick = onOpenGenerator, modifier = Modifier.weight(1f)) {
                    Text("New address")
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    sublabel: String,
    onClick: () -> Unit,
) {
    GlassCard(modifier = modifier, onClick = onClick) {
        Column {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
