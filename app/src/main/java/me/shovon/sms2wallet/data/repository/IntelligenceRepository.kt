package me.shovon.sms2wallet.data.repository

import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity
import me.shovon.sms2wallet.data.local.dao.CategoryRuleDao
import me.shovon.sms2wallet.data.local.entity.CategoryRuleEntity
import me.shovon.sms2wallet.data.prefs.AppPreferences
import me.shovon.sms2wallet.data.prefs.SecureTokenStore
import me.shovon.sms2wallet.data.remote.NaturalLanguageParser
import me.shovon.sms2wallet.data.remote.CategorySuggestionResult
import me.shovon.sms2wallet.data.remote.NlParseResult
import me.shovon.sms2wallet.domain.category.LocalCategoryResolver
import me.shovon.sms2wallet.domain.category.MerchantCategoryGuesser
import me.shovon.sms2wallet.domain.nlp.CategoryPrompt
import me.shovon.sms2wallet.domain.model.WalletLabels
import me.shovon.sms2wallet.domain.model.idFor
import me.shovon.sms2wallet.domain.model.labelFor
import me.shovon.sms2wallet.domain.model.labels
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
    private val categoryRuleDao: CategoryRuleDao,
) {

    val settings: Flow<IntelligenceSettings> = appPreferences.intelligenceSettings

    /** True once an API key is stored; the entry point stays hidden until it is. */
    val isConfigured: Flow<Boolean> = secureTokenStore.hasGeminiApiKey

    suspend fun saveApiKey(apiKey: String) = secureTokenStore.saveGeminiApiKey(apiKey)

    suspend fun clearApiKey() = secureTokenStore.clearGeminiApiKey()

    suspend fun setModel(model: String) = appPreferences.setGeminiModel(model)

    suspend fun setShareCategoryNames(share: Boolean) = appPreferences.setShareCategoryNames(share)

    suspend fun setShareAccountNames(share: Boolean) = appPreferences.setShareAccountNames(share)

    suspend fun setShareMerchantNames(share: Boolean) = appPreferences.setShareMerchantNames(share)

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

        // Labels, not raw names: the model picks one of these strings and the add screen
        // resolves it straight back to an id, so an ambiguous name here would put the
        // transaction under whichever same-named category happened to be first.
        val categoryLabels = WalletLabels.forCategories(categories)
        val accountLabels = WalletLabels.forAccounts(accounts)

        val result = parser.parse(
            input = input,
            categoryNames = if (current.shareCategoryNames) categoryLabels.labels() else emptyList(),
            accountNames = if (current.shareAccountNames) accountLabels.labels() else emptyList(),
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
                        categoryName = resolveCategoryLabel(parsed.categoryName, parsed.title, categories),
                        accountName = resolveAccountLabel(parsed.accountName, current.defaultAccountId, accounts),
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
     * Suggests a category for each of [subjects], returning the ones it could answer.
     *
     * Local first, always: a learned rule or the built-in merchant table answers for free and
     * instantly, and only merchants neither knows are sent to Google - as one batched request,
     * de-duplicated, so ten rows from the same shop cost one entry rather than ten.
     *
     * Merchants a suggestion could not be found for are simply absent from the result. The
     * caller leaves those fields as they were rather than filling them with a guess.
     */
    suspend fun suggestCategories(subjects: List<CategorySubject>): CategorySuggestions {
        if (subjects.isEmpty()) return CategorySuggestions.Success(emptyMap(), usedAi = false)

        val current = settings.first()
        val categories = walletSyncRepository.categories.first()
        if (categories.isEmpty()) {
            return CategorySuggestions.Failure("Connect Wallet in Settings to sync your categories first.")
        }

        val resolved = mutableMapOf<Long, String>()
        val unresolved = mutableListOf<CategorySubject>()

        for (subject in subjects) {
            val rules = categoryRuleDao.findApplicableRules(subject.bankName)
            val local = LocalCategoryResolver.resolve(subject.merchant, rules, categories)
            if (local != null) resolved[subject.transactionId] = local else unresolved += subject
        }

        if (unresolved.isEmpty()) return CategorySuggestions.Success(resolved, usedAi = false)
        if (!current.shareMerchantNames) {
            // The rest would need the merchant names sent off the device, which the user has
            // not agreed to. Whatever was resolved locally still stands.
            return CategorySuggestions.Success(
                categoryIdByTransactionId = resolved,
                usedAi = false,
                note = "Turn on merchant-name sharing in Settings to let Gemini try the rest.",
            )
        }

        val labels = WalletLabels.forCategories(categories)
        // One entry per distinct merchant: rows from the same shop share an answer.
        val byMerchant = unresolved
            .filter { !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!!.trim() }
        if (byMerchant.isEmpty()) return CategorySuggestions.Success(resolved, usedAi = false)

        val promptSubjects = byMerchant.map { (merchant, rows) ->
            CategoryPrompt.Subject(merchant = merchant, isIncome = rows.first().isIncome)
        }

        return when (val result = parser.classify(promptSubjects, labels.labels(), current.model)) {
            is CategorySuggestionResult.Success -> {
                result.labelByMerchant.forEach { (merchant, label) ->
                    val categoryId = labels.idFor(label) ?: return@forEach
                    byMerchant[merchant]?.forEach { row -> resolved[row.transactionId] = categoryId }
                }
                CategorySuggestions.Success(resolved, usedAi = true)
            }

            // A model failure must not discard what was already resolved on-device: those
            // answers cost nothing and are just as good. The reason travels with them.
            CategorySuggestionResult.NotConfigured ->
                partial(resolved, "Add a Gemini API key in Settings to match the rest.")
            CategorySuggestionResult.InvalidApiKey ->
                partial(resolved, "Your Gemini API key was rejected. Check it in Settings.")
            is CategorySuggestionResult.NetworkError ->
                partial(resolved, result.message ?: "No connection to Google.")
            is CategorySuggestionResult.HttpError ->
                partial(resolved, result.message ?: "Gemini returned HTTP ${result.status}.")
        }
    }

    /** Keeps locally-resolved answers when the model could not be reached. */
    private fun partial(resolved: Map<Long, String>, reason: String): CategorySuggestions =
        if (resolved.isEmpty()) {
            CategorySuggestions.Failure(reason)
        } else {
            CategorySuggestions.Success(resolved, usedAi = false, note = reason)
        }

    /**
     * Remembers that [merchant] belongs in [walletCategoryId], so the next transaction from it
     * is answered on-device.
     *
     * Called when the user *confirms* a transaction, never when a suggestion is offered: the
     * confirmation is the signal. Learning from the suggestion itself would let one wrong guess
     * teach itself as a rule and repeat forever.
     */
    suspend fun rememberCategory(merchant: String?, bankName: String?, walletCategoryId: String?) {
        if (walletCategoryId.isNullOrBlank()) return
        val keyword = LocalCategoryResolver.keywordFor(merchant) ?: return

        val existing = categoryRuleDao.findApplicableRules(bankName.orEmpty())
            .firstOrNull { it.keyword.equals(keyword, ignoreCase = true) }
        if (existing != null && existing.walletCategoryId == walletCategoryId) return

        categoryRuleDao.upsert(
            CategoryRuleEntity(
                id = existing?.id ?: 0,
                keyword = keyword,
                walletCategoryId = walletCategoryId,
                priority = LEARNED_RULE_PRIORITY,
                // Learned rules apply across banks: the same shop shows up through whichever
                // card or wallet was used, and the category does not change with the sender.
                bankName = null,
            )
        )
    }

    /**
     * Matches the model's category name to a real one, falling back to the on-device guesser.
     *
     * The match is exact-but-case-insensitive rather than fuzzy: the model chose from an enum of
     * these exact names, so anything that does not match one is a bug or a stale catalogue, and
     * quietly resolving it to a *near* category would file money in the wrong place.
     */
    private fun resolveCategoryLabel(
        modelChoice: String?,
        merchant: String,
        categories: List<WalletCategoryEntity>,
    ): String? {
        val labels = WalletLabels.forCategories(categories)

        modelChoice
            ?.let { choice -> labels.firstOrNull { it.label.equals(choice, ignoreCase = true) } }
            ?.let { return it.label }

        val guessedId = MerchantCategoryGuesser.guess(merchant, categories) ?: return null
        return labels.labelFor(guessedId)
    }

    /** The model's choice, else the user's default account, else whatever is first. */
    private fun resolveAccountLabel(
        modelChoice: String?,
        defaultAccountId: String?,
        accounts: List<WalletAccountEntity>,
    ): String? {
        val labels = WalletLabels.forAccounts(accounts)

        modelChoice
            ?.let { choice -> labels.firstOrNull { it.label.equals(choice, ignoreCase = true) } }
            ?.let { return it.label }

        return labels.labelFor(defaultAccountId) ?: labels.labels().firstOrNull()
    }

    /**
     * Renders the amount for a text field: no exponent, and no trailing ".00" to delete before
     * typing over it.
     */
    private fun BigDecimal.toPlainAmountText(): String =
        stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
}

private const val LEARNED_RULE_PRIORITY = 100

/** One transaction awaiting a category suggestion. */
data class CategorySubject(
    val transactionId: Long,
    val merchant: String?,
    val isIncome: Boolean,
    val bankName: String,
)

/** What [IntelligenceRepository.suggestCategories] hands back. */
sealed interface CategorySuggestions {

    /**
     * [usedAi] is false when everything was answered on-device. [note] explains why the model
     * was not consulted, or why it failed - carried alongside the results rather than instead
     * of them, so a key problem never discards work already done for free.
     */
    data class Success(
        val categoryIdByTransactionId: Map<Long, String>,
        val usedAi: Boolean,
        val note: String? = null,
    ) : CategorySuggestions

    data object NotConfigured : CategorySuggestions

    data class Failure(val message: String) : CategorySuggestions
}

/** What [IntelligenceRepository.parse] hands back to the UI. */
sealed interface IntelligenceResult {

    data class Success(val prefill: NlPrefill) : IntelligenceResult

    /** No API key stored - the UI should send the user to Settings rather than show an error. */
    data object NotConfigured : IntelligenceResult

    data class Failure(val message: String) : IntelligenceResult
}
