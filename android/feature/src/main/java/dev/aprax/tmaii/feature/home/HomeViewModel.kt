package dev.aprax.tmaii.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.DomainRepository
import dev.aprax.tmaii.core.repository.InboxRepository
import dev.aprax.tmaii.core.repository.SessionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val unread: Int = 0,
    val domainCount: Int = 0,
    val verifiedDomains: Int = 0,
    val lastAddress: String? = null,
    val email: String? = null,
    val backendOk: Boolean? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val domains: DomainRepository,
    private val inbox: InboxRepository,
    private val session: SessionRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    var state by mutableStateOf(HomeUiState())
        private set

    init { refresh() }

    fun refresh() {
        state = state.copy(loading = state.domainCount == 0 && state.unread == 0, error = null)
        viewModelScope.launch {
            try {
                session.ensureBackendConfigured()
                val email = settings.email.first()
                val lastAddress = settings.lastAddress.first()
                val domainList = async { domains.listDomains() }
                val unread = async { inbox.unreadCount() }
                val health = async { runCatching { session.health() } }
                val d = domainList.await()
                state = HomeUiState(
                    loading = false,
                    unread = unread.await(),
                    domainCount = d.size,
                    verifiedDomains = d.count { it.verified },
                    lastAddress = lastAddress,
                    email = email,
                    backendOk = health.await().getOrNull()?.status == "ok",
                )
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    error = e.message ?: "Could not load dashboard",
                    backendOk = false,
                )
            }
        }
    }
}
