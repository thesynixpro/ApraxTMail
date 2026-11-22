package dev.aprax.tmaii.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.SessionRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {

    var state by mutableStateOf(AccountUiState())
        private set

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var isRegister by mutableStateOf(true)
        private set

    fun onEmailChange(v: String) { email = v; state = state.copy(error = null) }
    fun onPasswordChange(v: String) { password = v; state = state.copy(error = null) }
    fun toggleMode() { isRegister = !isRegister; state = state.copy(error = null) }

    fun submit() {
        val e = email.trim()
        if (e.isEmpty() || !e.contains("@")) {
            state = state.copy(error = "Enter a valid email address")
            return
        }
        if (password.length < 10) {
            state = state.copy(error = "Password must be at least 10 characters")
            return
        }
        state = AccountUiState(loading = true)
        viewModelScope.launch {
            try {
                if (isRegister) session.register(e, password) else session.login(e, password)
                state = AccountUiState(signedIn = true)
            } catch (ex: Exception) {
                state = AccountUiState(error = (ex as? ApiException)?.message ?: ex.message ?: "Sign in failed")
            }
        }
    }
}
