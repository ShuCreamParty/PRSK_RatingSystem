package com.example.sekairatingsystem.ui

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    VIRTUAL_SINGER,
    LEO_NEED,
    MORE_MORE_JUMP,
    VIVID_BAD_SQUAD,
    WONDERLANDS_SHOWTIME,
    NIGHTCORD;

    companion object {
        fun fromPreference(value: String?): ThemeMode {
            return values().firstOrNull { mode -> mode.name == value } ?: SYSTEM
        }
    }

    fun resolveDarkTheme(systemDark: Boolean): Boolean {
        return when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
            VIRTUAL_SINGER,
            LEO_NEED,
            MORE_MORE_JUMP,
            VIVID_BAD_SQUAD,
            WONDERLANDS_SHOWTIME,
            NIGHTCORD -> systemDark
        }
    }
}
