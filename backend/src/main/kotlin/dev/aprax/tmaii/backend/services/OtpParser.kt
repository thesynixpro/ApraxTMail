package dev.aprax.tmaii.backend.services

/**
 * Conservative local OTP extraction used as a fallback when the provider
 * does not return an actual code (FreeCustom free plan returns "__DETECTED__",
 * Tmailor OTP is best-effort). Never fabricates codes: only matches patterns
 * that are clearly verification codes in context.
 */
object OtpParser {

    private val codePatterns = listOf(
        Regex("""(?i)(?:code|otp|pin|password|verification(?:\s+code)?|one[-\s]?time(?:\s+(?:password|code))?|2fa|auth(?:entication)?\s+code)\D{0,20}(\d{4,8})\b"""),
        Regex("""(?i)\b(\d{4,8})\s*(?:is\s+your|(?:is\s+)?your\s+(?:verification\s+)?code)\b"""),
        Regex("""(?i)\b(?:code|otp|pin)\s*[:#]?\s*(\d{4,8})\b"""),
        Regex("""(?i)\b(\d{6})\b(?=[^\d]*(?:verify|confirm|sign\s*in|log\s*in|authenticate|one[-\s]?time))"""),
    )

    private val linkPatterns = listOf(
        Regex("""https?://[^\s"'<>()]+(?:verify|confirm|activate|auth|otp|token|magic)[^\s"'<>()]*""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s"'<>()]*\?(?:[^&\s]*&)*(?:token|code|otp|verify|hash)=[^\s"'<>&]+""", RegexOption.IGNORE_CASE),
    )

    private val excludedContext = Regex(
        """(?i)(order|invoice|amount|total|price|balance|transaction|reference|invoice\s*number|phone|tel|mobile|date|birthday|amount\s*due)""",
    )

    /** Returns a plausible OTP found in [raw], or null. */
    fun extract(raw: String): String? {
        if (raw.isBlank()) return null
        val text = stripHtml(raw)
        for (pattern in codePatterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues.getOrNull(1) ?: continue
            if (isPlausibleOtp(candidate, text, match.range)) return candidate
        }
        return null
    }

    /** Returns all plausible OTPs (best-first). */
    fun extractAll(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val text = stripHtml(raw)
        val found = LinkedHashSet<String>()
        for (pattern in codePatterns) {
            for (match in pattern.findAll(text)) {
                val candidate = match.groupValues.getOrNull(1) ?: continue
                if (isPlausibleOtp(candidate, text, match.range)) found.add(candidate)
            }
        }
        return found.toList()
    }

    fun findVerificationLinks(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
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

    private fun isPlausibleOtp(candidate: String, text: String, range: IntRange): Boolean {
        if (candidate.length !in 4..8) return false
        if (candidate.all { it == '0' } || candidate.all { it == '1' }) return false
        // Reject sequences that are obviously dates/amounts/phones in surrounding context
        val contextStart = (range.first - 60).coerceAtLeast(0)
        val contextEnd = (range.last + 60).coerceAtMost(text.length)
        val context = text.substring(contextStart, contextEnd)
        if (excludedContext.containsMatchIn(context) && !context.contains(Regex("""(?i)code"""))) {
            // Allow if the word "code" appears near — overrides amount/phone context
            val near = text.substring(range.first.coerceAtLeast(0), (range.last + 30).coerceAtMost(text.length))
            if (!near.contains(Regex("""(?i)code|otp|pin|verify"""))) return false
        }
        // Phone-like: 10-11 digits would have been rejected by length already (max 8)
        return true
    }

    fun stripHtml(raw: String): String {
        if (!raw.contains('<')) return raw
        return raw
            .replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), " ")
            .replace(Regex("""(?s)<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Basic HTML sanitizer for rendering email bodies in-app.
     * Strips scripts/styles/event handlers and dangerous URLs. Backend also
     * serves sanitized HTML; Android applies its own sanitizer before WebView.
     */
    fun sanitizeHtml(raw: String): String {
        if (raw.isBlank()) return ""
        var html = raw
            .replace(Regex("""(?is)<(script|style|iframe|object|embed|form|meta|link)[^>]*>.*?</\1>"""), "")
            .replace(Regex("""(?is)<(script|style|iframe|object|embed|form|meta|link)[^>]*/?>"""), "")
            .replace(Regex("""(?is)\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)"""), "")
            .replace(Regex("""(?is)javascript\s*:"""), "")
            .replace(Regex("""(?is)href\s*=\s*("|')\s*javascript:[^"']*("|')"""), "href=\"#\"")
            .replace(Regex("""(?is)src\s*=\s*("|')\s*javascript:[^"']*("|')"""), "src=\"\"")
            .replace(Regex("""(?is)<(img|video|audio|source|link)[^>]*\ssrc\s*=\s*("|')\s*https?://[^"']*("|')""")) { m ->
                // Remote images are allowed but Android blocks them by default; keep tag.
                m.value
            }
            .replace(Regex("""(?is)<img([^>]*?)\ssrc\s*=\s*("|')(?!https?:|data:)[^"']*\2""")) { m ->
                m.value.replace(Regex("""(?is)src\s*=\s*("|')[^"']*\1"""), "src=\"\"")
            }
        // Remove data: URIs except images
        html = html.replace(Regex("""(?is)(src|href)\s*=\s*("|')\s*data:text/html[^"']*\2"""), "$1=\"#\"")
        return html
    }
}
