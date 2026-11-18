package dev.openlabel.core.printer.calibration

import android.content.Context
import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.label.model.MediaType
import dev.openlabel.core.label.model.PrinterDpi

class PrinterProfileStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("printer-profiles", Context.MODE_PRIVATE)

    fun save(address: String, profile: LabelProfile) {
        preferences.edit()
            .putString("$address.name", profile.name)
            .putFloat("$address.width", profile.widthMm)
            .putFloat("$address.height", profile.heightMm)
            .putInt("$address.dpi", profile.dpi.dotsPerInch)
            .putString("$address.media", profile.mediaType.name)
            .putFloat("$address.gap", profile.gapMm)
            .apply()
    }

    fun load(address: String): LabelProfile? {
        val width = preferences.getFloat("$address.width", -1f)
        val height = preferences.getFloat("$address.height", -1f)
        if (width <= 0f || height <= 0f) return null
        val dpi = if (preferences.getInt("$address.dpi", 203) == 300) PrinterDpi.DPI_300 else PrinterDpi.DPI_203
        val media = runCatching { MediaType.valueOf(preferences.getString("$address.media", MediaType.GAP.name)!!) }.getOrDefault(MediaType.GAP)
        return runCatching {
            LabelProfile(
                name = preferences.getString("$address.name", "Мой профиль") ?: "Мой профиль",
                widthMm = width,
                heightMm = height,
                dpi = dpi,
                mediaType = media,
                gapMm = preferences.getFloat("$address.gap", 2f),
            )
        }.getOrNull()
    }
}
