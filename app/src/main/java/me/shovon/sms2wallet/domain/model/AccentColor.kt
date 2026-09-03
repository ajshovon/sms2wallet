package me.shovon.sms2wallet.domain.model

/**
 * The seed colour the app's Material 3 palette is generated from.
 *
 * Stored as a stable enum name rather than a raw colour value: the actual hex is a design
 * decision that may be retuned, and a persisted `0xFF1B5E4A` would pin a user to whatever the
 * value happened to be on the day they chose it.
 */
enum class AccentColor {
    /**
     * Material You - the palette follows the device wallpaper.
     *
     * Only meaningful on Android 12+; below that the app falls back to [BRAND].
     */
    DYNAMIC,

    /** The app's own deep teal-green, and the fallback wherever dynamic colour is unavailable. */
    BRAND,
    BLUE,
    VIOLET,
    ROSE,
    AMBER,
    FOREST;

    companion object {
        fun fromName(value: String?): AccentColor =
            entries.firstOrNull { it.name == value } ?: DYNAMIC
    }
}
