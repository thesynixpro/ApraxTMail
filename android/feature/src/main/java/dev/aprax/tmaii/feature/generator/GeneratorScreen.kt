package dev.aprax.tmaii.feature.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.feature.common.LoadingState
import dev.aprax.tmaii.feature.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    homeViewModel: HomeViewModel = hiltViewModel(),
    viewModel: GeneratorViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.createdAddress) {
        state.createdAddress?.let {
            snackbarHostState.showSnackbar("Created $it")
            viewModel.consumeCreated()
            onBack()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Email Generator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Provider selection
            Text("Provider", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.providers.filter { it.enabled && it.configured }.forEach { p ->
                    FilterChip(
                        selected = state.selectedProvider == p.id,
                        onClick = { viewModel.selectProvider(p.id) },
                        label = { Text(p.displayName) },
                    )
                }
                if (state.providers.none { it.enabled && it.configured }) {
                    Text(
                        "No providers configured. Add API keys to the backend environment.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val selected = state.providers.firstOrNull { it.id == state.selectedProvider }
            if (selected != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Capabilities", style = MaterialTheme.typography.labelLarge)
                        val caps = buildList {
                            if (selected.capabilities.randomAddress) add("Random address")
                            if (selected.capabilities.customName) add("Custom username")
                            if (selected.capabilities.domainSelection) add("Domain selection")
                            if (selected.capabilities.providerOtpEndpoint || selected.capabilities.otpFromMessage) add("OTP extraction")
                            if (selected.capabilities.verificationLinks) add("Verification links")
                            if (selected.capabilities.reuseUrl) add("Reuse URL")
                            if (!selected.capabilities.attachments) add("No attachments")
                            selected.capabilities.messageRetentionHours?.let { add("Messages kept ${it}h") }
                        }
                        Text(
                            caps.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.quotas[selected.id]?.let { q ->
                            Text(
                                listOfNotNull(
                                    q.plan?.let { "Plan: $it" },
                                    q.mailboxesRemaining?.let { "Mailboxes left: $it" },
                                    q.dailyRequestsRemaining?.let { "Daily requests left: $it" },
                                ).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Mode
            Text("Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.randomMode,
                    onClick = { viewModel.setRandomMode(true) },
                    label = { Text("Random address") },
                )
                FilterChip(
                    selected = !state.randomMode,
                    onClick = { viewModel.setRandomMode(false) },
                    label = { Text("Custom") },
                )
            }

            if (!state.randomMode) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Letters, digits, dots (5–30 chars)") },
                )

                if (state.domains.isNotEmpty()) {
                    Text("Domain", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.domains.take(6).forEach { d ->
                            FilterChip(
                                selected = state.domain == d.domain,
                                onClick = { viewModel.setDomain(d.domain) },
                                label = { Text(d.domain, style = MaterialTheme.typography.bodySmall) },
                            )
                        }
                    }
                }
            }

            val quota = state.quotas[state.selectedProvider]
            if (quota?.mailboxesRemaining == 0L || quota?.dailyRequestsRemaining == 0L) {
                Text(
                    "Provider quota exhausted — try the other provider or wait for reset.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = { viewModel.create() },
                enabled = !state.creating && state.selectedProvider.isNotBlank() &&
                    (state.randomMode || (state.username.isNotBlank() && state.domain.isNotBlank())),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.creating) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Creating…")
                } else {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate")
                }
            }

            if (homeState.activeMailbox != null) {
                OutlinedButton(
                    onClick = { homeViewModel.generateRandom(state.selectedProvider) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Quick random with ${state.selectedProvider}")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
