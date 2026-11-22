package dev.aprax.tmaii.feature.generator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.feature.common.EmptyState
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.GradientCard
import dev.aprax.tmaii.feature.common.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    onAddedDomain: () -> Unit,
    onOpenInbox: () -> Unit,
    viewModel: GeneratorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var dropdownOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Email generator") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )

        when {
            state.loading -> LoadingState(label = "Loading domains...")
            state.domains.isEmpty() -> EmptyState(
                title = "No domains yet",
                subtitle = "Add your custom domain first, then generate addresses on it.",
                actionLabel = "Add a domain",
                onAction = onAddedDomain,
                icon = Icons.Outlined.Casino,
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedVisibility(visible = state.createdAddress != null, enter = fadeIn(), exit = fadeOut()) {
                    state.createdAddress?.let { address ->
                        GradientCard {
                            Column {
                                Text("Address created", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        address,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy address")
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = viewModel::reset) { Text("Create another") }
                                    Button(onClick = onOpenInbox) { Text("Open inbox") }
                                }
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = dropdownOpen,
                    onExpandedChange = { dropdownOpen = it },
                ) {
                    val selected = state.domains.firstOrNull { it.id == state.selectedDomainId }
                    OutlinedTextField(
                        value = selected?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Domain") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownOpen,
                        onDismissRequest = { dropdownOpen = false },
                    ) {
                        state.domains.forEach { d ->
                            DropdownMenuItem(
                                text = {
                                    Row {
                                        Text(d.name)
                                        if (d.verified) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "verified",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.selectDomain(d.id)
                                    dropdownOpen = false
                                },
                            )
                        }
                    }
                }

                if (state.domains.none { it.verified }) {
                    Text(
                        "Your domains are not verified yet — mail will only arrive after DNS checks pass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                OutlinedTextField(
                    value = viewModel.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Username") },
                    singleLine = true,
                    supportingText = {
                        Text("Letters, digits, dots, dashes, underscores")
                    },
                    trailingIcon = {
                        IconButton(onClick = viewModel::randomize) {
                            Icon(Icons.Outlined.Casino, contentDescription = "Random username")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = viewModel::create,
                    enabled = !state.creating,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.creating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Generate address")
                    }
                }

                GlassCard {
                    Column {
                        Text("How it works", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "New mail is delivered by Emailit to your domain and appears in the inbox within seconds. Messages expire automatically after 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}