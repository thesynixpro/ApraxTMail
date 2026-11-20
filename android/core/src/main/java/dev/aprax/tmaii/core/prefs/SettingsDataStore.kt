package dev.aprax.tmaii.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.aprax.tmaii.core.model.RefreshInterval
import dev.aprax.tmaii.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val AUTH_ROLE = stringPreferencesKey("auth_role")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val THEME = stringPreferencesKey("theme")
        val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.BASE_URL] ?: "" }
    val authToken: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_TOKEN] }
    val authRole: Flow<String?> = context.dataStore.data.map { it[Keys.AUTH_ROLE] }
    val userId: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ID] }
    val email: Flow<String?> = context.dataStore.data.map { it[Keys.EMAIL] }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }
    val refreshInterval: Flow<RefreshInterval> = context.dataStore.data.map {
        RefreshInterval.fromSeconds(it[Keys.REFRESH_SECONDS] ?: 30)
    }
    val defaultProvider: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_PROVIDER] ?: "tmailor" }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val autoRefreshEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_REFRESH_ENABLED] ?: true }

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[Keys.BASE_URL] = url.trimEnd('/') }

    suspend fun setSession(token: String?, role: String?, userId: String?, email: String?) {
        context.dataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(Keys.AUTH_TOKEN)
                prefs.remove(Keys.AUTH_ROLE)
                prefs.remove(Keys.USER_ID)
                prefs.remove(Keys.EMAIL)
            } else {
                prefs[Keys.AUTH_TOKEN] = token
                role?.let { prefs[Keys.AUTH_ROLE] = it }
                userId?.let { prefs[Keys.USER_ID] = it }
                email?.let { prefs[Keys.EMAIL] = it }
            }
        }
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setRefreshInterval(interval: RefreshInterval) =
        context.dataStore.edit { it[Keys.REFRESH_SECONDS] = interval.seconds }

    suspend fun setDefaultProvider(provider: String) =
        context.dataStore.edit { it[Keys.DEFAULT_PROVIDER] = provider }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_REFRESH_ENABLED] = enabled }

    suspend fun clearSession() = setSession(null, null, null, null)
}
