package dev.aprax.tmaii.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.MessageDto
import dev.aprax.tmaii.feature.common.EmptyState
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.LoadingState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenMessage: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Inbox", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            FilterChip(
                selected = state.unreadOnly,
                onClick = { viewModel.setUnreadOnly(!state.unreadOnly) },
                label = { Text("Unread only") },
            )
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }

        when {
            state.loading -> LoadingState()
            state.error != null && state.messages.isEmpty() -> {
                ErrorState(message = state.error ?: "Could not load messages", onRetry = { viewModel.refresh() })
            }
            state.messages.isEmpty() -> EmptyState(
                title = "No messages yet",
                subtitle = if (state.unreadOnly) "No unread messages right now."
                else "Generate an address and mail will show up here.",
                icon = Icons.AutoMirrored.Outlined.MarkEmailRead,
            )
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageRow(msg = msg, onClick = { onOpenMessage(msg.id) })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        state.error?.let { err ->
            if (state.messages.isNotEmpty()) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageRow(msg: MessageDto, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!msg.read) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(end = 0.dp)
                                .let { it },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.MarkEmailUnread,
                                contentDescription = "Unread",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        msg.fromName?.takeIf { it.isNotBlank() } ?: msg.from ?: "Unknown sender",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (msg.read) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (msg.hasAttachments) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Has attachments",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    msg.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTime(msg.receivedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    msg.otpCode?.let { code ->
                        Spacer(Modifier.width(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "OTP $code",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val now = System.currentTimeMillis()
    return when {
        now - epochMs < 60_000 -> "just now"
        now - epochMs < 3_600_000 -> "${(now - epochMs) / 60_000}m ago"
        else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(epochMs))
    }
}
