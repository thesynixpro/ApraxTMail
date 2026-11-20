package dev.aprax.tmaii.core.otp

/**
 * Local OTP fallback used for cached messages when the backend/provider
 * has not returned a code. Mirrors backend logic; never fabricates codes.
 */
object OtpParser {

    private val codePatterns = listOf(
        Regex("""(?i)(?:code|otp|pin|password|verification(?:\s+code)?|one[-\s]?time(?:\s+(?:password|code))?|2fa|auth(?:entication)?\s+code)\D{0,20}(\d{4,8})\b"""),
        Regex("""(?i)\b(\d{4,8})\s*(?:is\s+your|(?:is\s+)?your\s+(?:verification\s+)?code)\b"""),
        Regex("""(?i)\b(?:code|otp|pin)\s*[:#]?\s*(\d{4,8})\b"""),
        Regex("""(?i)\b(\d{6})\b(?=[^\d]*(?:verify|confirm|sign\s*in|log\s*in|authenticate|one[-\s]?time)))"""),
    )

    private val linkPatterns = listOf(
        Regex("""https?://[^\s"'<>()]+(?:verify|confirm|activate|auth|otp|token|magic)[^\s"'<>()]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s"'<>()]*\?(?:[^&\s]*&)*(?:token|code|otp|verify|hash)=[^\s"'<>&]+""", RegexOption.IGNORE_CASE),
    )

    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = stripHtml(raw)
        for (pattern in codePatterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues.getOrNull(1) ?: continue
            if (candidate.length in 4..8 && !candidate.all { it == '0' }) {
                return candidate
            }
        }
        return null
    }

    fun findVerificationLinks(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = stripHtml(raw)
        val links = LinkedHashSet<String>()
        for (pattern in linkPatterns) {
            pattern.findAll(text).forEach { m ->
                val url = m.value.trimEnd('.', ',', ')', ']')
                if (url.startsWith("http")) links.add(url)
            }
        }
        return links.take(10)
    }

    fun stripHtml(raw: String): String {
        if (!raw.contains('<')) return raw
        return raw
            .replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), " ")
            .replace(Regex("""(?s)<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Sanitizes HTML for Android WebView rendering.
     * JavaScript is disabled at the WebView level; this removes handlers,
     * script/style blocks, and javascript: URLs as defense in depth.
     */
    fun sanitizeHtml(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var html = raw
            .replace(Regex("""(?is)<(script|style|iframe|object|embed|form|meta|link)[^>]*>.*?</\1>"""), "")
            .replace(Regex("""(?is)<(script|style|iframe|object|embed|form|meta|link)[^>]*/?>"""), "")
            .replace(Regex("""(?is)\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)"""), "")
            .replace(Regex("""(?is)javascript\s*:"""), "")
            .replace(Regex("""(?is)href\s*=\s*("|')\s*javascript:[^"']*("|')"""), "href=\"#\"")
            .replace(Regex("""(?is)src\s*=\s*("|')\s*javascript:[^"']*("|')"""), "src=\"\"")
        html = html.replace(Regex("""(?is)(src|href)\s*=\s*("|')\s*data:text/html[^"']*\2"""), "$1=\"#\"")
        return html
    }
}
