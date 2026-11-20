package dev.aprax.tmaii.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.Mailbox
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GradientCard
import dev.aprax.tmaii.feature.common.LoadingState
import dev.aprax.tmaii.feature.common.StatusDot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenGenerator: () -> Unit,
    onOpenInbox: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    when {
        state.isLoading && state.activeMailbox == null && state.providers.isEmpty() -> {
            LoadingState(label = "Connecting to Aprax TMaii…")
        }
        state.error != null && state.activeMailbox == null && state.backendStatus == "offline" -> {
            ErrorState(message = state.error ?: "Offline", onRetry = viewModel::retry)
        }
        else -> HomeContent(
            state = state,
            onGenerate = { onOpenGenerator() },
            onCopy = { address ->
                clipboard.setText(AnnotatedString(address))
                viewModel.consumeMessage()
            },
            onRefresh = { viewModel.refreshMessages() },
            onDelete = { viewModel.deleteActiveMailbox() },
            onOpenInbox = onOpenInbox,
            onRetry = viewModel::retry,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onOpenInbox: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Brand header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Aprax TMaii", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Temporary email, done right",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusDot(
                color = when (state.backendStatus) {
                    "connected" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                },
                label = state.backendStatus,
            )
        }

        val mailbox = state.activeMailbox
        if (mailbox == null) {
            EmptyActiveCard(onGenerate = onGenerate, generating = state.isGenerating)
        } else {
            ActiveMailboxCard(
                mailbox = mailbox,
                state = state,
                onCopy = onCopy,
                onRefresh = onRefresh,
                onDelete = onDelete,
                onOpenInbox = onOpenInbox,
                onGenerate = onGenerate,
            )
        }

        // Provider status
        if (state.providers.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Providers", style = MaterialTheme.typography.titleMedium)
                    state.providers.forEach { p ->
                        val quota = state.quotas[p.id]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StatusDot(
                                color = when {
                                    !p.enabled -> MaterialTheme.colorScheme.outline
                                    !p.configured -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                label = "",
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.displayName, style = MaterialTheme.typography.bodyLarge)
                                val status = when {
                                    !p.enabled -> "Disabled"
                                    !p.configured -> "API key missing"
                                    else -> quota?.plan?.let { "Plan: $it" } ?: "Ready"
                                }
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (quota?.mailboxesRemaining != null) {
                                Text(
                                    "${quota.mailboxesRemaining} left",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyActiveCard(generating: Boolean, onGenerate: () -> Unit) {
    GradientCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.Email,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("No active mailbox", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Generate a temporary address to start receiving email.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGenerate, enabled = !generating) {
                if (generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Generating…")
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Email")
                }
            }
        }
    }
}

@Composable
private fun ActiveMailboxCard(
    mailbox: Mailbox,
    state: HomeUiState,
    onCopy: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onOpenInbox: () -> Unit,
    onGenerate: () -> Unit,
) {
    GradientCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        mailbox.label ?: mailbox.provider.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        mailbox.address,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onCopy(mailbox.address) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy email")
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenInbox,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Inbox (${state.messageCount})")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !state.isRefreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onGenerate) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New")
                }
                Spacer(Modifier.weight(1f))
                val quota = state.quotas[mailbox.provider]
                Text(
                    quota?.let { q ->
                        listOfNotNull(
                            q.plan?.let { "Plan $it" },
                            q.mailboxesRemaining?.let { "$it mailboxes" },
                        ).joinToString(" · ")
                    } ?: "Quota n/a",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }

            state.lastRefreshAt?.let { ts ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "Last refresh: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
