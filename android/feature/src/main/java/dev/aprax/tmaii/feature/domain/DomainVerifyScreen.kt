package dev.aprax.tmaii.feature.domain

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
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.DnsRecordDto
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.GradientCard
import dev.aprax.tmaii.feature.common.LoadingState
import dev.aprax.tmaii.feature.common.StatusDot

@Composable
fun DomainVerifyScreen(
    onBack: () -> Unit,
    viewModel: DomainVerifyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    val domain = state.domain

    when {
        state.loading -> LoadingState(label = "Loading domain...")
        domain == null -> ErrorState(message = state.error ?: "Domain not found", onRetry = viewModel::load)
        else -> Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(domain.name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete domain", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GradientCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Verified,
                            contentDescription = null,
                            tint = if (domain.verified) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (domain.verified) "Domain verified" else "Waiting for DNS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (domain.verified) "Mail can arrive on this domain"
                                else "Add the records below, then tap Verify",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusDot(
                            color = if (domain.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            label = domain.status,
                        )
                    }
                }

                GlassCard {
                    Column {
                        Text("Add these at your DNS host", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Keep your nameservers unchanged. Create each record exactly as shown — verification checks live DNS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                domain.records.forEach { record ->
                    RecordCard(record = record, onCopy = { clipboard.setText(AnnotatedString(it)) })
                }

                if (domain.records.isEmpty()) {
                    Text(
                        "No DNS records stored for this domain. Delete and re-add it.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.check?.let { check ->
                    GlassCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Last check", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                StatusDot(
                                    color = if (check.allOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    label = if (check.allOk) "all records OK" else "incomplete",
                                )
                            }
                            if (check.missing.isNotEmpty()) {
                                Text(
                                    "Missing: ${check.missing.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            check.checks.forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(
                                        color = if (c.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        label = "${c.type} ${c.name}",
                                    )
                                }
                            }
                        }
                    }
                }

                state.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = viewModel::verify,
                    enabled = !state.verifying,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Verify DNS now")
                    }
                }

                OutlinedButton(
                    onClick = viewModel::load,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reload status") }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${domain?.name}?") },
            text = { Text("Removes the domain, its addresses and inbox messages. Your DNS records are untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecordCard(record: DnsRecordDto, onCopy: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (!record.required) {
                    Text("optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onCopy(record.value) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy value", modifier = Modifier.size(18.dp))
                }
            }
            Text("Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.host, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Text("Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            record.priority?.let { p ->
                Spacer(Modifier.height(6.dp))
                Text("Priority: $p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
