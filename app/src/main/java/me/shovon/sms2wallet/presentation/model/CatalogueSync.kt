package me.shovon.sms2wallet.presentation.model

import me.shovon.sms2wallet.data.remote.ApiResult

/**
 * Shown when a sync succeeded but BudgetBakers said it was still applying recent changes.
 * Without it, an account created in Wallet moments ago is simply absent, with nothing to say that
 * waiting and syncing again is the fix rather than a fault in this app.
 */
const val SERVER_STILL_SYNCING_NOTE =
    "Wallet is still processing recent changes, so an account you just added may not be here yet. " +
        "Sync again in a few minutes."

/**
 * What to tell the user after they asked for the accounts and categories to be synced.
 *
 * Success says what arrived, so a missing account is noticed straight away rather than the next
 * time a picker is opened.
 */
fun catalogueSyncMessage(result: ApiResult<Unit>, accountCount: Int, categoryCount: Int): String = when (result) {
    is ApiResult.Success -> if (result.serverStillSyncing) {
        SERVER_STILL_SYNCING_NOTE
    } else {
        "Synced ${plural(accountCount, "account")} and ${plural(categoryCount, "category", "categories")} from Wallet."
    }
    ApiResult.Unauthorized -> "Wallet rejected your token, so nothing was synced. Check it in Settings."
    is ApiResult.SyncInProgress ->
        "Wallet is still doing its first sync - try again in ${result.retryAfterMinutes ?: DEFAULT_RETRY_MINUTES} minutes."
    is ApiResult.RateLimited -> "Wallet is rate limiting requests - try again shortly."
    is ApiResult.NetworkError -> "Couldn't reach Wallet. Check your connection."
    is ApiResult.HttpError -> "Wallet returned an error (${result.status})."
    is ApiResult.InvalidRequest -> result.message
}

private fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"

private const val DEFAULT_RETRY_MINUTES = 5
