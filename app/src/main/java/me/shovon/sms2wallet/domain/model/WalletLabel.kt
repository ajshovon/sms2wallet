package me.shovon.sms2wallet.domain.model

import me.shovon.sms2wallet.data.local.entity.WalletAccountEntity
import me.shovon.sms2wallet.data.local.entity.WalletCategoryEntity

/**
 * A Wallet category or account paired with the text the user sees for it.
 *
 * The pickers work in text, but a name is not an identity: Wallet allows sub-categories, so two
 * categories can both be called "Other" under different parents, and two accounts can share a
 * name too. Resolving a picked name back with `first { it.name == picked }` then silently
 * returns whichever happened to be first, and the transaction is filed against the wrong id.
 *
 * [WalletLabels] builds labels that are unique within a list, so a label round-trips to exactly
 * one id and the user can tell two same-named entries apart.
 */
data class WalletLabel(val id: String, val label: String)

object WalletLabels {

    /**
     * Labels categories, disambiguating repeats by parent name ("Other (Food)").
     *
     * The parent is the distinction the user actually thinks in; only when that still collides -
     * or the parent is missing - does it fall back to a fragment of the id, which is meaningless
     * to read but at least keeps the two entries selectable and distinct.
     */
    fun forCategories(categories: List<WalletCategoryEntity>): List<WalletLabel> {
        val nameById = categories.associate { it.id to it.name }
        return disambiguate(
            items = categories.map { it.id to it.name },
            qualifier = { id -> categories.firstOrNull { it.id == id }?.parentId?.let(nameById::get) },
        )
    }

    /** Labels accounts, disambiguating repeats by currency ("Cash (USD)"). */
    fun forAccounts(accounts: List<WalletAccountEntity>): List<WalletLabel> = disambiguate(
        items = accounts.map { it.id to it.name },
        qualifier = { id -> accounts.firstOrNull { it.id == id }?.currencyCode?.takeIf { it.isNotBlank() } },
    )

    private fun disambiguate(
        items: List<Pair<String, String>>,
        qualifier: (String) -> String?,
    ): List<WalletLabel> {
        val duplicated = items.groupingBy { it.second }.eachCount().filterValues { it > 1 }.keys
        val used = mutableSetOf<String>()

        return items.map { (id, name) ->
            var label = if (name in duplicated) {
                qualifier(id)?.let { "$name ($it)" } ?: name
            } else {
                name
            }
            // A qualifier can repeat too (two "Other" under the same parent), and a name can
            // even collide with another entry's qualified label. The id fragment is the last
            // resort that cannot collide.
            if (!used.add(label)) {
                label = "$name (${id.take(ID_FRAGMENT_LENGTH)})"
                var suffix = ID_FRAGMENT_LENGTH
                while (!used.add(label) && suffix < id.length) {
                    suffix++
                    label = "$name (${id.take(suffix)})"
                }
            }
            WalletLabel(id = id, label = label)
        }
    }

    private const val ID_FRAGMENT_LENGTH = 4
}

/** The picker options, in list order. */
fun List<WalletLabel>.labels(): List<String> = map { it.label }

/** The id behind a picked label, or null if the catalogue no longer has it. */
fun List<WalletLabel>.idFor(label: String): String? =
    firstOrNull { it.label == label }?.id

/** The label to show for a stored id, or null when the id is unknown or absent. */
fun List<WalletLabel>.labelFor(id: String?): String? =
    id?.let { wanted -> firstOrNull { it.id == wanted }?.label }
