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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.feature.common.GlassCard

@Composable
fun AddDomainScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: AddDomainViewModel = hiltViewModel(),
) {
    val name = viewModel.name
    val submitting = viewModel.submitting
    val error = viewModel.error

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Add domain") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlassCard {
                Row {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your nameservers stay exactly where they are. You only add a few DNS records at your current provider — we show the exact values next.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Domain name") },
                placeholder = { Text("example.com") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    if (error != null) Text(error)
                    else Text("Registered with Emailit automatically when you continue")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { viewModel.submit(onCreated) },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Add domain")
                }
            }

            Text(
                "Next you'll see the exact MX/TXT records Emailit requires. Add them at your DNS host, then tap Verify.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
