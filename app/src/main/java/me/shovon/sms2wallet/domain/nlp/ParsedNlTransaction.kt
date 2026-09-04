package me.shovon.sms2wallet.domain.nlp

import java.math.BigDecimal

/**
 * One transaction the model read out of a typed phrase, before any of it has been matched
 * against the user's Wallet catalogue.
 *
 * Names, not ids: the model only ever sees names, so this is what it can return. Resolving
 * [categoryName]/[accountName] to real ids - and deciding what to do when they are absent - is
 * the repository's job, not the parser's.
 */
data class ParsedNlTransaction(
    val amount: BigDecimal,
    val isIncome: Boolean,
    val title: String,
    val note: String? = null,
    val categoryName: String? = null,
    val accountName: String? = null,
)
