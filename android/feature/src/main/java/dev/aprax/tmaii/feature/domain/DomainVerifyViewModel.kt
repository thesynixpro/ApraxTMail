package dev.aprax.tmaii.feature.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.DnsCheckDto
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.core.repository.DomainRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyUiState(
    val loading: Boolean = true,
    val verifying: Boolean = false,
    val error: String? = null,
    val domain: DomainDto? = null,
    val check: DnsCheckDto? = null,
    val lastCheckedAt: Long? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class DomainVerifyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DomainRepository,
) : ViewModel() {

    private val domainId: String = checkNotNull(savedStateHandle["domainId"])

    var state by mutableStateOf(VerifyUiState())
        private set

    init { load() }

    fun load() {
        state = state.copy(loading = state.domain == null, error = null)
        viewModelScope.launch {
            try {
                val domain = repo.getDomain(domainId)
                state = VerifyUiState(
                    loading = false,
                    domain = domain,
                    check = state.check,
                    lastCheckedAt = domain.lastCheckedAt,
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Could not load domain")
            }
        }
    }

    fun verify() {
        state = state.copy(verifying = true, error = null)
        viewModelScope.launch {
            try {
                val resp = repo.verifyDomain(domainId)
                state = state.copy(
                    verifying = false,
                    domain = resp.domain,
                    check = resp.check,
                    lastCheckedAt = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                state = state.copy(verifying = false, error = e.message ?: "Verification failed")
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteDomain(domainId)
                state = state.copy(deleted = true)
                onDone()
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "Delete failed")
            }
        }
    }
}
