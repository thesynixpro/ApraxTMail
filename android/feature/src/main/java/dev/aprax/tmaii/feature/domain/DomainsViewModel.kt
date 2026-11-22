package dev.aprax.tmaii.feature.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.core.repository.DomainRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DomainsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val domains: List<DomainDto> = emptyList(),
)

@HiltViewModel
class DomainsViewModel @Inject constructor(
    private val repo: DomainRepository,
) : ViewModel() {

    var state by mutableStateOf(DomainsUiState())
        private set

    init { refresh(initial = true) }

    fun refresh(initial: Boolean = false) {
        state = state.copy(refreshing = !initial, error = null)
        viewModelScope.launch {
            try {
                val list = repo.listDomains()
                state = DomainsUiState(loading = false, domains = list)
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Could not load domains",
                )
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteDomain(id)
                state = state.copy(domains = state.domains.filterNot { it.id == id })
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "Delete failed")
            }
        }
    }
}
