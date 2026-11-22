package dev.aprax.tmaii.feature.navigation

object Routes {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val DOMAINS = "domains"
    const val SETTINGS = "settings"
    const val GENERATOR = "generator"
    const val MESSAGE_DETAIL = "message/{messageId}"
    const val ADD_DOMAIN = "domains/add"
    const val DOMAIN_VERIFY = "domains/{domainId}/verify"
    const val ACCOUNT = "account"

    fun messageDetail(messageId: String) = "message/$messageId"
    fun domainVerify(domainId: String) = "domains/$domainId/verify"
}
