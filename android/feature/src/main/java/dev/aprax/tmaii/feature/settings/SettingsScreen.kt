package dev.aprax.tmaii.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.Accent
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.GradientCard

private val accentSwatches = mapOf(
    Accent.VIOLET to androidx.compose.ui.graphics.Color(0xFF8B7CF6),
    Accent.BLUE to androidx.compose.ui.graphics.Color(0xFF4F8CFF),
    Accent.TEAL to androidx.compose.ui.graphics.Color(0xFF2DD4BF),
    Accent.GREEN to androidx.compose.ui.graphics.Color(0xFF4ADE80),
    Accent.AMBER to androidx.compose.ui.graphics.Color(0xFFFBBF24),
    Accent.ROSE to androidx.compose.ui.graphics.Color(0xFFFB7185),
)

private val themeLabels = mapOf(
    ThemeMode.LIGHT to "Light",
    ThemeMode.DARK to "Dark",
    ThemeMode.SYSTEM to "System",
    ThemeMode.LIQUID_GLASS to "Liquid Glass",
)

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // --- Appearance ---
        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { viewModel.setTheme(mode) },
                    label = { Text(themeLabels[mode] ?: mode.name, maxLines = 1) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Accent color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(accent.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Accent.entries.forEach { a ->
                val swatch = accentSwatches[a] ?: MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (accent == a) 3.dp else 1.dp,
                            color = if (accent == a) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = CircleShape,
                        )
                        .clickable { viewModel.setAccent(a) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (accent == a) {
                        Icon(
                            Icons.Outlined.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }

        // Live preview
        Text("Live preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Preview card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "This reacts to theme and accent instantly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(selected = true, onClick = {}, label = { Text("Active") })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}) { Text("Primary") }
                    OutlinedButton(onClick = {}) { Text("Outline") }
                }
            }
        }

        // --- Backend ---
        SectionTitle("Backend connection")
        OutlinedTextField(
            value = viewModel.urlInput.ifBlank { baseUrl },
            onValueChange = viewModel::onUrlChange,
            label = { Text("Worker base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveUrl, modifier = Modifier.weight(1f)) {
                Text(if (viewModel.urlSaved) "Saved ✓" else "Save URL")
            }
            OutlinedButton(onClick = viewModel::testConnection, enabled = !viewModel.busy, modifier = Modifier.weight(1f)) {
                Text(if (viewModel.busy) "Testing…" else "Test connection")
            }
        }
        viewModel.healthMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Account ---
        SectionTitle("Account")
        GlassCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(email ?: "Not signed in", style = MaterialTheme.typography.titleSmall)
                        Text("JWT session, 30 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(email ?: ""))
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy email")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.logout(onSignedOut) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // --- About ---
        SectionTitle("About")
        GlassCard {
            Column {
                Text("ApraxTMail", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Custom-domain temporary mail. Inbound via Emailit, stored on Cloudflare (D1 + R2). Messages expire after 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
