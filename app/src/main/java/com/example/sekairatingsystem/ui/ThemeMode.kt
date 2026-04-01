package com.example.sekairatingsystem.ui

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromPreference(value: String?): ThemeMode {
            return values().firstOrNull { mode -> mode.name == value } ?: SYSTEM
        }
    }
}
