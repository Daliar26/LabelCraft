package dev.openlabel.core.printer.hardware

import android.view.KeyEvent

enum class HardwareAction { SCROLL_UP, SCROLL_DOWN, PAGE_UP, PAGE_DOWN, NONE }

data class HardwareScrollProfile(
    val deviceAddress: String,
    val stepMm: Float = 4f,
    val speedMultiplier: Float = 1f,
    val acceleration: Float = 1f,
    val inverted: Boolean = false,
    val consumeVolumeKeys: Boolean = true,
    val keyMap: Map<Int, HardwareAction> = defaultKeyMap(),
) {
    init {
        require(stepMm in 0.5f..30f)
        require(speedMultiplier in 0.25f..5f)
        require(acceleration in 0.5f..4f)
    }

    companion object {
        fun defaultKeyMap() = mapOf(
            KeyEvent.KEYCODE_VOLUME_UP to HardwareAction.SCROLL_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN to HardwareAction.SCROLL_DOWN,
            KeyEvent.KEYCODE_DPAD_UP to HardwareAction.SCROLL_UP,
            KeyEvent.KEYCODE_DPAD_DOWN to HardwareAction.SCROLL_DOWN,
            KeyEvent.KEYCODE_PAGE_UP to HardwareAction.PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN to HardwareAction.PAGE_DOWN,
        )
    }
}

object HardwareKeyMapper {
    fun map(keyCode: Int, profile: HardwareScrollProfile): HardwareAction {
        val action = profile.keyMap[keyCode] ?: HardwareAction.NONE
        if (!profile.inverted) return action
        return when (action) {
            HardwareAction.SCROLL_UP -> HardwareAction.SCROLL_DOWN
            HardwareAction.SCROLL_DOWN -> HardwareAction.SCROLL_UP
            HardwareAction.PAGE_UP -> HardwareAction.PAGE_DOWN
            HardwareAction.PAGE_DOWN -> HardwareAction.PAGE_UP
            HardwareAction.NONE -> HardwareAction.NONE
        }
    }

    fun deltaPx(action: HardwareAction, profile: HardwareScrollProfile, density: Float, dpi: Int, repeated: Int = 0): Float {
        if (action == HardwareAction.NONE) return 0f
        val direction = if (action == HardwareAction.SCROLL_UP || action == HardwareAction.PAGE_UP) -1f else 1f
        val pageScale = if (action == HardwareAction.PAGE_UP || action == HardwareAction.PAGE_DOWN) 5f else 1f
        val repeatScale = (1f + repeated.coerceAtMost(12) * 0.12f) * profile.acceleration
        val pxPerMm = dpi / 25.4f * density
        return direction * profile.stepMm * pxPerMm * profile.speedMultiplier * pageScale * repeatScale
    }
}

class HardwareScrollProfileStore(context: android.content.Context) {
    private val preferences = context.applicationContext.getSharedPreferences("hardware-scroll-profiles", android.content.Context.MODE_PRIVATE)

    fun save(profile: HardwareScrollProfile) {
        val mapping = profile.keyMap.entries.joinToString(",") { "${it.key}:${it.value.name}" }
        preferences.edit()
            .putFloat("${profile.deviceAddress}.step", profile.stepMm)
            .putFloat("${profile.deviceAddress}.speed", profile.speedMultiplier)
            .putFloat("${profile.deviceAddress}.acceleration", profile.acceleration)
            .putBoolean("${profile.deviceAddress}.inverted", profile.inverted)
            .putBoolean("${profile.deviceAddress}.consume", profile.consumeVolumeKeys)
            .putString("${profile.deviceAddress}.mapping", mapping)
            .apply()
    }

    fun load(deviceAddress: String): HardwareScrollProfile {
        val defaults = HardwareScrollProfile(deviceAddress)
        val raw = preferences.getString("$deviceAddress.mapping", null)
        val mapping = raw?.split(',')?.mapNotNull { entry ->
            val parts = entry.split(':', limit = 2)
            if (parts.size != 2) null else runCatching { parts[0].toInt() to HardwareAction.valueOf(parts[1]) }.getOrNull()
        }?.toMap() ?: defaults.keyMap
        return HardwareScrollProfile(
            deviceAddress = deviceAddress,
            stepMm = preferences.getFloat("$deviceAddress.step", defaults.stepMm),
            speedMultiplier = preferences.getFloat("$deviceAddress.speed", defaults.speedMultiplier),
            acceleration = preferences.getFloat("$deviceAddress.acceleration", defaults.acceleration),
            inverted = preferences.getBoolean("$deviceAddress.inverted", defaults.inverted),
            consumeVolumeKeys = preferences.getBoolean("$deviceAddress.consume", defaults.consumeVolumeKeys),
            keyMap = mapping,
        )
    }
}
