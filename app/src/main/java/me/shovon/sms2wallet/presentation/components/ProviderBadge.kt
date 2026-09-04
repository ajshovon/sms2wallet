package me.shovon.sms2wallet.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.shovon.sms2wallet.presentation.model.TransactionDirection
import me.shovon.sms2wallet.presentation.theme.SolarIcons
import me.shovon.sms2wallet.presentation.theme.Spacing

/**
 * Visual styling data for Bangladeshi financial providers (MFS & Banks).
 */
data class ProviderVisuals(
    val abbreviation: String,
    val brandColor: Color,
    val containerColorLight: Color,
    val onContainerLight: Color,
    val containerColorDark: Color,
    val onContainerDark: Color,
)

object ProviderThemeRegistry {
    private val Bkash = ProviderVisuals(
        abbreviation = "bK",
        brandColor = Color(0xFFE2136E),
        containerColorLight = Color(0xFFFFE4EC),
        onContainerLight = Color(0xFF9E0047),
        containerColorDark = Color(0xFF520726),
        onContainerDark = Color(0xFFFFB2D0),
    )

    private val Nagad = ProviderVisuals(
        abbreviation = "Ng",
        brandColor = Color(0xFFF7941D),
        containerColorLight = Color(0xFFFFF0DC),
        onContainerLight = Color(0xFF9B4F00),
        containerColorDark = Color(0xFF4E2600),
        onContainerDark = Color(0xFFFFB878),
    )

    private val Rocket = ProviderVisuals(
        abbreviation = "Rk",
        brandColor = Color(0xFF8C3494),
        containerColorLight = Color(0xFFF5E4F8),
        onContainerLight = Color(0xFF5E1764),
        containerColorDark = Color(0xFF3F0B44),
        onContainerDark = Color(0xFFE7B1ED),
    )

    private val Upay = ProviderVisuals(
        abbreviation = "Up",
        brandColor = Color(0xFF0A2B4C),
        containerColorLight = Color(0xFFFFF7D6),
        onContainerLight = Color(0xFF5E4900),
        containerColorDark = Color(0xFF3B3000),
        onContainerDark = Color(0xFFFFE270),
    )

    private val Tap = ProviderVisuals(
        abbreviation = "Tap",
        brandColor = Color(0xFF00A3E0),
        containerColorLight = Color(0xFFE0F6FF),
        onContainerLight = Color(0xFF00587C),
        containerColorDark = Color(0xFF00374F),
        onContainerDark = Color(0xFF82D6FF),
    )

    private val CityBank = ProviderVisuals(
        abbreviation = "CB",
        brandColor = Color(0xFF003865),
        containerColorLight = Color(0xFFE1EEFA),
        onContainerLight = Color(0xFF004177),
        containerColorDark = Color(0xFF00294D),
        onContainerDark = Color(0xFFA6CCF2),
    )

    private val BracBank = ProviderVisuals(
        abbreviation = "BR",
        brandColor = Color(0xFF005696),
        containerColorLight = Color(0xFFE3EDF7),
        onContainerLight = Color(0xFF003C6A),
        containerColorDark = Color(0xFF002847),
        onContainerDark = Color(0xFF9CC8F0),
    )

    private val EasternBank = ProviderVisuals(
        abbreviation = "EBL",
        brandColor = Color(0xFF004B87),
        containerColorLight = Color(0xFFE2F0F9),
        onContainerLight = Color(0xFF003D6F),
        containerColorDark = Color(0xFF002747),
        onContainerDark = Color(0xFFA0CEF2),
    )

    private val MutualTrustBank = ProviderVisuals(
        abbreviation = "MTB",
        brandColor = Color(0xFF008852),
        containerColorLight = Color(0xFFE1F6EC),
        onContainerLight = Color(0xFF005934),
        containerColorDark = Color(0xFF003820),
        onContainerDark = Color(0xFF8AE0B7),
    )

    private val Cash = ProviderVisuals(
        abbreviation = "Cash",
        brandColor = Color(0xFF1B7A43),
        containerColorLight = Color(0xFFE3F7EB),
        onContainerLight = Color(0xFF0D542B),
        containerColorDark = Color(0xFF09331A),
        onContainerDark = Color(0xFF8FE6B3),
    )

    private val IslamiBank = ProviderVisuals(
        abbreviation = "IBBL",
        brandColor = Color(0xFF008542),
        containerColorLight = Color(0xFFE2F6EA),
        onContainerLight = Color(0xFF005429),
        containerColorDark = Color(0xFF00381B),
        onContainerDark = Color(0xFF8CE2B4),
    )

    private val StandardChartered = ProviderVisuals(
        abbreviation = "SCB",
        brandColor = Color(0xFF007A9E),
        containerColorLight = Color(0xFFE0F3FA),
        onContainerLight = Color(0xFF004D64),
        containerColorDark = Color(0xFF003343),
        onContainerDark = Color(0xFF87D8F2),
    )

    fun resolve(providerName: String?): ProviderVisuals {
        val name = providerName?.trim().orEmpty().lowercase()
        return when {
            name.contains("bkash") -> Bkash
            name.contains("nagad") -> Nagad
            name.contains("rocket") || name.contains("dbbl") -> Rocket
            name.contains("upay") -> Upay
            name.contains("tap") -> Tap
            name.contains("city") -> CityBank
            name.contains("brac") -> BracBank
            name.contains("eastern") || name.contains("ebl") -> EasternBank
            name.contains("mutual") || name.contains("mtb") -> MutualTrustBank
            name.contains("islami") || name.contains("ibbl") -> IslamiBank
            name.contains("standard") || name.contains("scb") -> StandardChartered
            name.contains("cash") -> Cash
            else -> ProviderVisuals(
                abbreviation = providerName?.take(2)?.uppercase()?.ifBlank { "TX" } ?: "TX",
                brandColor = Color(0xFF3D6373),
                containerColorLight = Color(0xFFE1EAF0),
                onContainerLight = Color(0xFF223E4C),
                containerColorDark = Color(0xFF1A2E38),
                onContainerDark = Color(0xFFB4CDDC),
            )
        }
    }
}

/**
 * Modern provider avatar badge. Displays provider abbreviation / glyph with signature brand
 * styling in a soft tinted container with high contrast, and optional direction marker.
 */
@Composable
fun ProviderAvatar(
    providerName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    direction: TransactionDirection? = null,
) {
    val visuals = ProviderThemeRegistry.resolve(providerName)
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val containerColor = if (isDark) visuals.containerColorDark else visuals.containerColorLight
    val contentColor = if (isDark) visuals.onContainerDark else visuals.onContainerLight

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.32f))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = visuals.abbreviation,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (visuals.abbreviation.length > 2) (size.value * 0.30f).sp else (size.value * 0.38f).sp,
                letterSpacing = (-0.5).sp
            )
        }

        if (direction != null) {
            val isIncome = direction == TransactionDirection.INCOME
            val dirColor = if (isIncome) Color(0xFF1B7A43) else Color(0xFFBA1A1A)
            val dirIcon = if (isIncome) SolarIcons.ArrowDownLeft else SolarIcons.ArrowUpRight

            Box(
                modifier = Modifier
                    .size(size * 0.44f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(dirColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = dirIcon,
                    contentDescription = null,
                    tint = dirColor,
                    modifier = Modifier.size(size * 0.28f)
                )
            }
        }
    }
}

/**
 * Compact pill showing provider avatar and name for headers, quote bars, or detail chips.
 */
@Composable
fun ProviderChip(
    providerName: String,
    modifier: Modifier = Modifier
) {
    val visuals = ProviderThemeRegistry.resolve(providerName)
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val containerColor = if (isDark) visuals.containerColorDark else visuals.containerColorLight
    val contentColor = if (isDark) visuals.onContainerDark else visuals.onContainerLight

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = visuals.abbreviation,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = " • $providerName",
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
