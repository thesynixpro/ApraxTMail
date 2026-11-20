package dev.aprax.tmaii.feature.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.database.LocalMailboxEntity
import dev.aprax.tmaii.feature.common.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onReopen: (mailboxId: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (items.isEmpty()) {
        EmptyState(
            title = "No history yet",
            subtitle = "Mailboxes you create will appear here so you can reopen them later.",
            icon = Icons.Filled.History,
        )
        return
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { mailbox ->
            HistoryRow(
                mailbox = mailbox,
                onOpen = { onReopen(mailbox.id) },
                onCopy = {
                    clipboard.setText(AnnotatedString(mailbox.address))
                    viewModel.announce("Copied ${mailbox.address}")
                },
                onDeleteRemote = { viewModel.delete(mailbox, remote = true) },
                onDeleteLocalOnly = { viewModel.delete(mailbox, remote = false) },
                onDeleteHistory = { viewModel.deleteFromHistory(mailbox) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    mailbox: LocalMailboxEntity,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDeleteRemote: () -> Unit,
    onDeleteLocalOnly: () -> Unit,
    onDeleteHistory: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        mailbox.address,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${mailbox.provider.uppercase()} · " +
                            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                                .format(Date(mailbox.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
                IconButton(onClick = onDeleteRemote) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete remote mailbox",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Reopen",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpen),
                )
                Text(
                    "Delete remote",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDeleteRemote),
                )
                Text(
                    "Remove from history",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDeleteHistory),
                )
            }
        }
    }
}
