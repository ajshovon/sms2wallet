package me.shovon.sms2wallet.data.repository

import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.prefs.SecureTokenStore
import me.shovon.sms2wallet.data.remote.NaturalLanguageParser
import me.shovon.sms2wallet.data.remote.NlParseResult
import me.shovon.sms2wallet.domain.category.MerchantCategoryGuesser
import me.shovon.sms2wallet.domain.model.IntelligenceSettings
import me.shovon.sms2wallet.domain.nlp.NlPrefill

/**
 * Natural-language transaction entry: decides what is sent, sends it, and resolves what comes
 * back against the user's own Wallet catalogue.
 *
 * The sharing toggles are enforced *here*, at the single point where the request is assembled,
 * rather than trusted to each caller. A caller cannot accidentally leak account names by
 * forgetting a flag, because callers do not get to pass the lists at all.
 */
class IntelligenceRepository @Inject constructor(
    private val parser: NaturalLanguageParser,
    private val appPreferences: AppPreferences,
    private val secureTokenStore: SecureTokenStore,
    private val walletSyncRepository: WalletSyncRepository,
) {

    val settings: Flow<IntelligenceSettings> = appPreferences.intelligenceSettings

    /** True once an API key is stored; the entry point stays hidden until it is. */
    val isConfigured: Flow<Boolean> = secureTokenStore.hasGeminiApiKey

    suspend fun saveApiKey(apiKey: String) = secureTokenStore.saveGeminiApiKey(apiKey)

    suspend fun clearApiKey() = secureTokenStore.clearGeminiApiKey()

    suspend fun setModel(model: String) = appPreferences.setGeminiModel(model)

    suspend fun setShareCategoryNames(share: Boolean) = appPreferences.setShareCategoryNames(share)

    suspend fun setShareAccountNames(share: Boolean) = appPreferences.setShareAccountNames(share)

    suspend fun setDefaultAccountId(accountId: String?) = appPreferences.setDefaultAccountId(accountId)

    /** @return null when the stored key and model check out, otherwise a message for the user. */
    suspend fun verifyApiKey(): String? = parser.verify(settings.first().model)

    /**
     * Parses [input] into a prefill for the add screen.
     *
     * Note that the returned category can come from the model *or* from the local
     * [MerchantCategoryGuesser]: with category sharing off, the model is never told what
     * categories exist, so the merchant name it extracts is classified on-device instead. Typing
     * "uber 120" therefore still lands on a transport category without Google being told a
     * single category name.
     */
    suspend fun parse(input: String): IntelligenceResult {
        val current = settings.first()
        val accounts = walletSyncRepository.accounts.first()
        val categories = walletSyncRepository.categories.first()

        val result = parser.parse(
            input = input,
            categoryNames = if (current.shareCategoryNames) categories.map { it.name } else emptyList(),
            accountNames = if (current.shareAccountNames) accounts.map { it.name } else emptyList(),
            model = current.model,
        )

        return when (result) {
            is NlParseResult.Success -> {
                val parsed = result.transaction
                IntelligenceResult.Success(
                    NlPrefill(
                        merchant = parsed.title,
                        // The prompt defines 0 as "no amount was stated", so leave the field
                        // empty for the user to fill rather than making them clear a literal 0.
                        amountText = if (parsed.amount.signum() == 0) "" else parsed.amount.toPlainAmountText(),
                        isIncome = parsed.isIncome,
                        categoryName = resolveCategoryName(parsed.categoryName, parsed.title, categories),
                        accountName = resolveAccountName(parsed.accountName, current.defaultAccountId, accounts),
                        note = parsed.note,
                    )
                )
            }

            NlParseResult.NotConfigured -> IntelligenceResult.NotConfigured
            NlParseResult.InvalidApiKey ->
                IntelligenceResult.Failure("Your Gemini API key was rejected. Check it in Settings.")
            NlParseResult.EmptyResult ->
                IntelligenceResult.Failure("Couldn't read a transaction from that. Try \"uber 120\".")
            is NlParseResult.NetworkError ->
                IntelligenceResult.Failure(result.message ?: "No connection to Google.")
            is NlParseResult.HttpError ->
                IntelligenceResult.Failure(result.message ?: "Gemini returned HTTP ${result.status}.")
        }
    }

    /**
     * Matches the model's category name to a real one, falling back to the on-device guesser.
     *
     * The match is exact-but-case-insensitive rather than fuzzy: the model chose from an enum of
     * these exact names, so anything that does not match one is a bug or a stale catalogue, and
     * quietly resolving it to a *near* category would file money in the wrong place.
     */
    private fun resolveCategoryName(
        modelChoice: String?,
        merchant: String,
        categories: List<WalletCategoryEntity>,
    ): String? {
        modelChoice
            ?.let { choice -> categories.firstOrNull { it.name.equals(choice, ignoreCase = true) } }
            ?.let { return it.name }

        val guessedId = MerchantCategoryGuesser.guess(merchant, categories) ?: return null
        return categories.firstOrNull { it.id == guessedId }?.name
    }

    /** The model's choice, else the user's default account, else whatever is first. */
    private fun resolveAccountName(
        modelChoice: String?,
        defaultAccountId: String?,
        accounts: List<WalletAccountEntity>,
    ): String? {
        modelChoice
            ?.let { choice -> accounts.firstOrNull { it.name.equals(choice, ignoreCase = true) } }
            ?.let { return it.name }

        return accounts.firstOrNull { it.id == defaultAccountId }?.name
            ?: accounts.firstOrNull()?.name
    }

    /**
     * Renders the amount for a text field: no exponent, and no trailing ".00" to delete before
     * typing over it.
     */
    private fun BigDecimal.toPlainAmountText(): String =
        stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
}

/** What [IntelligenceRepository.parse] hands back to the UI. */
sealed interface IntelligenceResult {

    data class Success(val prefill: NlPrefill) : IntelligenceResult

    /** No API key stored - the UI should send the user to Settings rather than show an error. */
    data object NotConfigured : IntelligenceResult

    data class Failure(val message: String) : IntelligenceResult
}
