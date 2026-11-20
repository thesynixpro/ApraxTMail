package dev.aprax.tmaii.backend.config

data class AppConfig(
    val port: Int,
    val appEnv: String,
    val publicBaseUrl: String,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val databaseMaxPoolSize: Int,
    val jwtSecret: String,
    val tokenEncryptionKey: String,
    val guestSessionHours: Long,
    val accountSessionHours: Long,
    val fceApiKey: String?,
    val tmailorApiKey: String?,
    val fceEnabled: Boolean,
    val tmailorEnabled: Boolean,
    val tmailorExpectedIp: String?,
    val adminEmail: String?,
    val adminPassword: String?,
    val rateLimitRps: Int,
    val guestMailboxLimit: Int,
    val accountMailboxLimit: Int,
    val corsOrigins: List<String>,
) {
    val isProduction: Boolean get() = appEnv.equals("production", ignoreCase = true)

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            fun str(key: String): String? = env[key]?.trim()?.takeIf { it.isNotEmpty() }
            fun int(key: String, default: Int): Int = str(key)?.toIntOrNull() ?: default
            fun long(key: String, default: Long): Long = str(key)?.toLongOrNull() ?: default
            fun bool(key: String, default: Boolean): Boolean = str(key)?.toBooleanStrictOrNull() ?: default

            val jwtSecret = str("JWT_SECRET")
                ?: throw IllegalStateException("JWT_SECRET is required. Generate with: openssl rand -hex 32")
            require(jwtSecret.length >= 32) { "JWT_SECRET must be at least 32 characters" }

            val encryptionKey = str("TOKEN_ENCRYPTION_KEY")
                ?: throw IllegalStateException("TOKEN_ENCRYPTION_KEY is required. Generate with: openssl rand -base64 32")

            return AppConfig(
                port = int("PORT", 8080),
                appEnv = str("APP_ENV") ?: "development",
                publicBaseUrl = str("PUBLIC_BASE_URL") ?: "http://localhost:${int("PORT", 8080)}",
                databaseUrl = str("DATABASE_URL")
                    ?: "jdbc:postgresql://localhost:5432/aprax_tmaii",
                databaseUser = str("DATABASE_USER") ?: "aprax",
                databasePassword = str("DATABASE_PASSWORD") ?: "aprax",
                databaseMaxPoolSize = int("DATABASE_MAX_POOL_SIZE", 10),
                jwtSecret = jwtSecret,
                tokenEncryptionKey = encryptionKey,
                guestSessionHours = long("GUEST_SESSION_HOURS", 72),
                accountSessionHours = long("ACCOUNT_SESSION_HOURS", 720),
                fceApiKey = str("FCE_API_KEY"),
                tmailorApiKey = str("TMAILOR_API_KEY"),
                fceEnabled = bool("FCE_ENABLED", true),
                tmailorEnabled = bool("TMAILOR_ENABLED", true),
                tmailorExpectedIp = str("TMAILOR_EXPECTED_IP"),
                adminEmail = str("ADMIN_EMAIL"),
                adminPassword = str("ADMIN_PASSWORD"),
                rateLimitRps = int("RATE_LIMIT_RPS", 5),
                guestMailboxLimit = int("GUEST_MAILBOX_LIMIT", 10),
                accountMailboxLimit = int("ACCOUNT_MAILBOX_LIMIT", 30),
                corsOrigins = (str("CORS_ORIGINS") ?: "http://localhost:8080")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
        }
    }
}
