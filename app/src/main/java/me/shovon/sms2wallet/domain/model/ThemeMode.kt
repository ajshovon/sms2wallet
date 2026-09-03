package me.shovon.sms2wallet.domain.model

/** How the app decides between its light and dark appearances. */
enum class ThemeMode {
    /** Follow the device's light/dark setting. */
    SYSTEM,
    LIGHT,
    DARK,

    /**
     * Dark, but with true-black backgrounds and surfaces.
     *
     * On an OLED panel a black pixel is an unlit pixel, so this genuinely saves power and removes
     * the faint grey glow that a normal dark theme shows in a dim room. It is a separate mode
     * rather than "dark, but darker" because the elevation model has to change with it: when the
     * background is #000 a surface can no longer be distinguished by being *slightly* lighter, so
     * the scheme leans on outlines instead.
     */
    AMOLED;

    companion object {
        /** Parses a stored name, falling back to [SYSTEM] for anything unrecognised. */
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
