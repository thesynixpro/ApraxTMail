package dev.aprax.tmaii.feature.inbox

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import dev.aprax.tmaii.core.database.LocalMessageEntity
import dev.aprax.tmaii.core.model.Message
import dev.aprax.tmaii.feature.common.EmptyState
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.LoadingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    activeMailboxId: String?,
    onOpenMessage: (messageId: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cached by viewModel.cachedMessages.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    DisposableEffect(activeMailboxId) {
        viewModel.attach(activeMailboxId)
        onDispose { viewModel.detach() }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    if (state.isLoading) {
        LoadingState(label = "Opening inbox…")
        return
    }

    if (state.mailbox == null) {
        EmptyState(
            title = "No mailbox selected",
            subtitle = "Generate an email address on the Home tab first.",
        )
        return
    }

    val messages: List<DisplayMessage> = if (state.remoteMessages.isNotEmpty()) {
        state.remoteMessages.map { it.toDisplay() }
    } else {
        cached.map { it.toDisplay() }
    }

    Column(Modifier.fillMaxSize()) {
        // OTP banner
        state.otp?.let { code ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Verification code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        state.otpSource?.let {
                            Text("Source: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code")
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            if (messages.isEmpty()) {
                EmptyState(
                    title = "Inbox is empty",
                    subtitle = "Waiting for mail at ${state.mailbox?.address}. Pull down to refresh.",
                    icon = Icons.Filled.MarkEmailRead,
                    actionLabel = "Refresh",
                    onAction = { viewModel.refresh() },
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageRow(msg = msg, onClick = { onOpenMessage(msg.id) })
                    }
                }
            }
        }
    }
}

data class DisplayMessage(
    val id: String,
    val from: String,
    val fromName: String?,
    val subject: String?,
    val receivedAt: String?,
    val seen: Boolean,
    val hasOtp: Boolean,
    val hasLink: Boolean,
)

private fun Message.toDisplay() = DisplayMessage(
    id = id,
    from = from,
    fromName = fromName,
    subject = subject,
    receivedAt = receivedAt,
    seen = seen,
    hasOtp = otp != null,
    hasLink = verificationLinks.isNotEmpty(),
)

private fun LocalMessageEntity.toDisplay() = DisplayMessage(
    id = providerMessageId,
    from = sender,
    fromName = senderName,
    subject = subject,
    receivedAt = receivedAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) },
    seen = seen,
    hasOtp = hasOtp,
    hasLink = hasVerificationLink,
)

@Composable
private fun MessageRow(msg: DisplayMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (msg.seen) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        msg.fromName?.takeIf { it.isNotBlank() } ?: msg.from,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (msg.seen) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        msg.from,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                msg.receivedAt?.let {
                    Text(
                        it.take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                msg.subject?.takeIf { it.isNotBlank() } ?: "(no subject)",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (msg.hasOtp || msg.hasLink) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (msg.hasOtp) {
                        Text(
                            "OTP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (msg.hasLink) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Verify link",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
