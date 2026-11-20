package dev.aprax.tmaii.feature.detail

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.otp.OtpParser
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    mailboxId: String,
    messageId: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: MessageDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(mailboxId, messageId) { viewModel.load(mailboxId, messageId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> LoadingState(Modifier.padding(padding), "Loading message…")
            detail == null -> ErrorState(
                message = state.error ?: "Message not available (it may have expired)",
                onRetry = { viewModel.load(mailboxId, messageId) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    detail.message.subject ?: "(no subject)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow("From", detail.message.fromName?.let { "$it <${detail.message.from}>" } ?: detail.message.from)
                        detail.message.to?.let { DetailRow("To", it) }
                        detail.message.receivedAt?.let { DetailRow("Received", it) }
                    }
                }

                // OTP card — provider first, then local parse (done by backend)
                detail.message.otp?.let { otp ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(otp["code"].orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "Detected from ${otp["source"] ?: "message body"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(otp["code"].orEmpty()))
                                snackbarHostState.let { /* fire and forget */ }
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy OTP")
                            }
                        }
                    }
                }

                // Verification links
                val links = detail.message.verificationLinks
                if (links.isNotEmpty()) {
                    Text("Verification links", style = MaterialTheme.typography.titleMedium)
                    links.forEach { url ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(url))
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                // Safe external navigation via system browser only
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open link", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Body
                Text("Body", style = MaterialTheme.typography.titleMedium)
                val html = detail.html
                val text = detail.text
                when {
                    !html.isNullOrBlank() -> SafeHtmlBody(html)
                    !text.isNullOrBlank() -> Text(text, style = MaterialTheme.typography.bodyMedium)
                    else -> Text(
                        "Body not available (provider did not return content).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * WebView with JavaScript disabled, no file/content access, and navigation
 * restricted to http(s) links which open in the system browser instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SafeHtmlBody(html: String) {
    val sanitized = OtpParser.sanitizeHtml(html)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkImage = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                            runCatching {
                                view?.context?.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            true // never navigate inside the WebView
                        } else {
                            true
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, sanitized, "text/html", "utf-8", null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
    )
}
