package me.shovon.sms2wallet.domain.model

/**
 * A sender ID the user has taught the app to treat as a particular provider.
 *
 * Mobile Number Portability can strip a bank's masked sender ID, so an MTB message arrives from
 * a bare phone number instead of "MTB". The parsers fall back to searching the body for a brand
 * token, but that only works when the message actually contains one - MTB's plain
 * "Dear Customer, Your A/C ... has been Debited" alerts carry no "MTB" anywhere, so they go
 * unmatched however good the parser is.
 *
 * Teaching the app the sender is exact and reversible, unlike widening a parser's body
 * heuristics, which risks claiming another bank's identically-shaped messages.
 */
data class SenderOverride(
    /** The sender ID as it arrives, compared case-insensitively after trimming. */
    val sender: String,
    /** [me.shovon.bdparser.bank.BankParser.getBankName] of the provider it belongs to. */
    val providerName: String,
) {
    companion object {
        private const val SEPARATOR = '|'

        /** Serialised for DataStore as `SENDER|Provider`. */
        fun encode(override: SenderOverride): String =
            "${normalise(override.sender)}$SEPARATOR${override.providerName}"

        fun decode(value: String): SenderOverride? {
            val separator = value.indexOf(SEPARATOR)
            if (separator <= 0 || separator == value.lastIndex) return null
            return SenderOverride(
                sender = value.substring(0, separator),
                providerName = value.substring(separator + 1),
            )
        }

        /** Senders are matched case-insensitively; store and look up the same normalised form. */
        fun normalise(sender: String): String = sender.trim().uppercase()
    }
}
