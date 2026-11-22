package dev.aprax.tmaii.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aprax.tmaii.core.model.Accent
import dev.aprax.tmaii.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "aprax_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
        val LAST_ADDRESS = stringPreferencesKey("last_address")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: "" }
    val authToken: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_TOKEN] }
    val userId: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val email: Flow<String?> = context.dataStore.data.map { it[Keys.EMAIL] }
    val lastAddress: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_ADDRESS] }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }
    val accent: Flow<Accent> = context.dataStore.data.map { Accent.fromName(it[Keys.ACCENT]) }

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[Keys.BASE_URL] = url.trimEnd('/') }

    suspend fun setSession(token: String?, userId: String?, email: String?) {
        context.dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(Keys.AUTH_TOKEN)
                prefs.remove(Keys.USER_ID)
                prefs.remove(Keys.EMAIL)
            } else {
                prefs[Keys.AUTH_TOKEN] = token
                userId?.let { prefs[Keys.USER_ID] = it }
                email?.let { prefs[Keys.EMAIL] = it }
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setAccent(accent: Accent) = context.dataStore.edit { it[Keys.ACCENT] = accent.name }

    suspend fun setLastAddress(address: String) = context.dataStore.edit { it[Keys.LAST_ADDRESS] = address }

    suspend fun clearSession() = setSession(null, null, null)
}
