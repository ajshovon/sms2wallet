package me.shovon.sms2wallet.domain.model

/**
 * Configuration for natural-language transaction entry.
 *
 * Every field here except [model] exists to answer one question: what leaves this device. The
 * feature sends a phrase the user typed to Google, so the user - not this code - decides how
 * much context goes with it. Withholding context makes the model less accurate, never more
 * private-by-accident: each toggle has a defined fallback so a withheld field degrades to a
 * local guess rather than to a wrong answer.
 */
data class IntelligenceSettings(
    val model: String = DEFAULT_MODEL,
    /**
     * Send the names of the user's Wallet categories so the model can pick one exactly.
     *
     * With this off the model is never asked for a category and [MerchantCategoryGuesser] does
     * the job locally from the merchant name it returned.
     */
    val shareCategoryNames: Boolean = true,
    /**
     * Send the names of the user's Wallet accounts so a phrase like "on bkash" can select one.
     *
     * Off by default: account names are the most identifying thing in the catalogue, and
     * [defaultAccountId] covers the common case where every typed entry lands in the same place.
     */
    val shareAccountNames: Boolean = false,
    /**
     * Send merchant names from parsed SMS so the model can suggest a category for them.
     *
     * Off by default, and separate from [shareCategoryNames], because it is a different class
     * of data: without it the only thing that ever leaves the device is a phrase the user typed
     * themselves. This sends names read out of their bank messages.
     */
    val shareMerchantNames: Boolean = false,
    /**
     * Account used for a typed entry when the model was not asked for an account, or named
     * none. Also the pre-selected account on the manual add screen.
     */
    val defaultAccountId: String? = null,
) {
    companion object {
        /**
         * A moving alias rather than a pinned version: Google retires dated model ids, and a
         * pinned default would strand every existing install the day it goes away.
         */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        val MODEL_OPTIONS = listOf(
            DEFAULT_MODEL,
            "gemini-flash-lite-latest",
            "gemini-2.5-flash",
        )
    }
}
