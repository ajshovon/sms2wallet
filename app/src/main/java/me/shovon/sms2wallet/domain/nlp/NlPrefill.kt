package me.shovon.sms2wallet.domain.nlp

/**
 * A parsed phrase, resolved against the user's Wallet catalogue and ready to populate the add
 * screen.
 *
 * Carries names rather than ids because that is what the form binds to, and because a name that
 * failed to resolve can still be shown to the user - an id that failed to resolve is just null.
 */
data class NlPrefill(
    val merchant: String,
    val amountText: String,
    val isIncome: Boolean,
    val categoryName: String? = null,
    val accountName: String? = null,
    val note: String? = null,
)
