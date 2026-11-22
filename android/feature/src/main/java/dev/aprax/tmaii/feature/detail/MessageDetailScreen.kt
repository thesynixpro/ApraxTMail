package dev.aprax.tmaii.feature.detail

import android.annotation.SuppressLint
import android.webkit.WebView
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.LoadingState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    onBack: () -> Unit,
    viewModel: MessageDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val msg = state.message

    when {
        state.loading -> LoadingState(label = "Loading message...")
        msg == null -> ErrorState(
            message = state.error ?: "Message not found",
            onRetry = { viewModel.load(refreshing = true) },
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Message", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(msg.text.ifBlank { msg.subject })) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy body")
                    }
                    IconButton(onClick = { viewModel.load(refreshing = true) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.delete(onBack) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    msg.subject,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            msg.fromName?.takeIf { it.isNotBlank() } ?: msg.from ?: "Unknown sender",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "to ${msg.to ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatTime(msg.receivedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                msg.otpCode?.let { code ->
                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Verification code",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    code,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                if (msg.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Attachments", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    msg.attachments.forEach { att ->
                        Text(
                            "• ${att.filename} (${formatSize(att.size)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !viewModel.showHtml,
                        onClick = { if (viewModel.showHtml) viewModel.toggleFormat() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Text") }
                    SegmentedButton(
                        selected = viewModel.showHtml,
                        onClick = { if (!viewModel.showHtml) viewModel.toggleFormat() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("HTML") }
                }
                Spacer(Modifier.height(12.dp))

                if (viewModel.showHtml && msg.html.isNotBlank()) {
                    HtmlBody(html = msg.html)
                } else if (msg.text.isNotBlank()) {
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "This message has no body.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            val bg = if (dark) "#0E1017" else "#FFFFFF"
            val fg = if (dark) "#E8EAF2" else "#14161C"
            webView.loadDataWithBaseURL(
                null,
                "<html><body style=\"background:$bg;color:$fg;font-family:sans-serif;padding:8px;margin:0;\">$html</body></html>",
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
    )
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMs))
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
