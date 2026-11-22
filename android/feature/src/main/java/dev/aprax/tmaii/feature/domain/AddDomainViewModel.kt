package dev.aprax.tmaii.feature.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.repository.DomainRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddDomainViewModel @Inject constructor(
    private val repo: DomainRepository,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun onNameChange(v: String) {
        name = v.trim().lowercase().removeSuffix(".")
        error = null
    }

    fun submit(onCreated: (String) -> Unit) {
        val domain = name.trim()
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) {
            error = "Enter a valid domain like example.com"
            return
        }
        submitting = true
        error = null
        viewModelScope.launch {
            try {
                val created = repo.addDomain(domain)
                submitting = false
                onCreated(created.id)
            } catch (e: Exception) {
                submitting = false
                error = e.message ?: "Could not add domain"
            }
        }
    }
}
