package dev.aprax.tmaii.feature.navigation

object Routes {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val GENERATOR = "generator"
    const val MESSAGE_DETAIL = "message/{mailboxId}/{messageId}"
    const val ACCOUNT = "account"

    fun messageDetail(mailboxId: String, messageId: String) =
        "message/$mailboxId/$messageId"
}
