package com.steerin.parkingmaster.data.reward

import android.content.Context
import com.steerin.parkingmaster.domain.reward.WheelSkin

/**
 * Unlocked skins and the chosen skin, kept apart from the session files so that history trimming
 * (50 sessions) or deleting a session never takes an earned skin away. On-device only.
 */
class RewardStore(context: Context) {
    private val prefs = context.getSharedPreferences("rewards", Context.MODE_PRIVATE)

    fun unlocked(): Set<WheelSkin> =
        prefs.getStringSet(KEY_UNLOCKED, emptySet()).orEmpty().mapNotNull { WheelSkin.fromId(it) }.toSet() + WheelSkin.CLASSIC

    fun saveUnlocked(skins: Set<WheelSkin>) {
        prefs.edit().putStringSet(KEY_UNLOCKED, skins.map { it.id }.toSet()).apply()
    }

    fun selected(): WheelSkin = WheelSkin.fromId(prefs.getString(KEY_SELECTED, null)) ?: WheelSkin.CLASSIC

    fun saveSelected(skin: WheelSkin) {
        prefs.edit().putString(KEY_SELECTED, skin.id).apply()
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_SELECTED = "selected"
    }
}
