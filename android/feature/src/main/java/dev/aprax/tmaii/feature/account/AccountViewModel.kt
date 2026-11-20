package dev.aprax.tmaii.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val busy: Boolean = false,
    val role: String? = null,
    val email: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state

    init {
        viewModelScope.launch {
            session.roleFlow.collect { role ->
                _state.value = _state.value.copy(role = role)
            }
        }
        viewModelScope.launch {
            session.emailFlow.collect { email ->
                _state.value = _state.value.copy(email = email)
            }
        }
        viewModelScope.launch {
            try {
                session.ensureSession()
            } catch (_: Exception) {
                // offline — role stays null until connectivity restored
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val resp = session.register(email, password)
                _state.value = _state.value.copy(
                    busy = false,
                    role = resp.role,
                    email = resp.email ?: email,
                    message = "Account created — guest mailboxes transferred",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = (e as? ApiException)?.message ?: e.message ?: "Registration failed",
                )
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val resp = session.login(email, password)
                _state.value = _state.value.copy(
                    busy = false,
                    role = resp.role,
                    email = resp.email ?: email,
                    message = "Signed in",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = (e as? ApiException)?.message ?: e.message ?: "Login failed",
                )
            }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                session.createGuest()
                _state.value = _state.value.copy(busy = false, role = "GUEST", message = "Guest session active")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    busy = false,
                    error = e.message ?: "Could not create guest session",
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            session.logout()
            _state.value = _state.value.copy(busy = false, role = null, email = null, message = "Signed out")
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
