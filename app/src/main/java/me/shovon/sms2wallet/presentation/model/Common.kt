package me.shovon.sms2wallet.presentation.model

/**
 * Whether a transaction removes money from an account (expense) or adds money to it
 * (income). Drives amount colour in the UI.
 */
enum class TransactionDirection {
    EXPENSE,
    INCOME
}
