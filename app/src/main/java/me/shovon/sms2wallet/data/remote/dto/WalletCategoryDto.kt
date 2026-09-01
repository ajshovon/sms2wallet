package me.shovon.sms2wallet.data.remote.dto

import kotlinx.serialization.Serializable

/** A Wallet category as returned by `GET /categories`. */
@Serializable
data class WalletCategoryDto(
    val id: String,
    val name: String? = null,
    /** Nullable slug, e.g. `food_and_drinks__groceries`, for well-known system categories. */
    val systemId: String? = null,
    val parentId: String? = null,
    val color: String? = null,
)
