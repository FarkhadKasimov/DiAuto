package com.andrerinas.openheadunit.ui

import android.content.Context

class SettingsTheme private constructor(
    val isOverlay: Boolean,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentTrack: Int,
    val trackOff: Int,
    val buttonText: Int,
    val ripple: Int,
    val focusRing: Int,
) {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        @JvmField val CARD = card(DiAutoPalette.DARK)
        @JvmField val OVERLAY = overlay(DiAutoPalette.DARK)
        @JvmField val entries = listOf(CARD, OVERLAY)

        fun card(palette: DiAutoPalette) = SettingsTheme(
            isOverlay = false,
            textPrimary = palette.primaryText,
            textSecondary = palette.secondaryText,
            accent = palette.accent,
            accentTrack = palette.accentTrack,
            trackOff = palette.trackOff,
            buttonText = palette.onAccent,
            ripple = palette.ripple,
            focusRing = palette.focusRing,
        )

        fun overlay(palette: DiAutoPalette) = SettingsTheme(
            isOverlay = true,
            textPrimary = palette.overlayPrimaryText,
            textSecondary = palette.overlaySecondaryText,
            accent = palette.overlayAccent,
            accentTrack = palette.overlayAccentTrack,
            trackOff = palette.overlayTrackOff,
            buttonText = palette.overlayOnAccent,
            ripple = palette.ripple,
            focusRing = palette.focusRing,
        )
    }
}
