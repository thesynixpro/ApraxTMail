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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.feature.common.EmptyState
import dev.aprax.tmaii.feature.common.ErrorState
import dev.aprax.tmaii.feature.common.GlassCard
import dev.aprax.tmaii.feature.common.LoadingState
import dev.aprax.tmaii.feature.common.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainsScreen(
    onAddDomain: () -> Unit,
    onOpenDomain: (String) -> Unit,
    viewModel: DomainsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DomainDto?>(null) }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Domains", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }

        when {
            state.loading -> LoadingState()
            state.error != null && state.domains.isEmpty() -> ErrorState(
                message = state.error ?: "Could not load domains",
                onRetry = { viewModel.refresh() },
            )
            state.domains.isEmpty() -> EmptyState(
                title = "No domains yet",
                subtitle = "Connect your own domain to start receiving mail. Nameservers never change.",
                actionLabel = "Add domain",
                onAction = onAddDomain,
                icon = Icons.Outlined.Domain,
            )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.domains, key = { it.id }) { domain ->
                    GlassCard(onClick = { onOpenDomain(domain.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(domain.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatusDot(
                                        color = if (domain.verified) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        label = if (domain.verified) "Verified" else "Pending",
                                    )
                                    StatusDot(
                                        color = if (domain.mxOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        label = "MX",
                                    )
                                    StatusDot(
                                        color = if (domain.txtOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        label = "TXT",
                                    )
                                }
                            }
                            IconButton(onClick = { pendingDelete = domain }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete ${domain.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        state.error?.let { err ->
            if (state.domains.isNotEmpty()) {
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    ExtendedFloatingActionButton(
        onClick = onAddDomain,
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        text = { Text("Add domain") },
        modifier = Modifier
            .align(androidx.compose.ui.Alignment.BottomEnd)
            .padding(20.dp),
    )
    }

    pendingDelete?.let { domain ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${domain.name}?") },
            text = {
                Text("Addresses on this domain and its inbox messages will be removed. DNS records at your provider are not touched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(domain.id)
                        pendingDelete = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}
