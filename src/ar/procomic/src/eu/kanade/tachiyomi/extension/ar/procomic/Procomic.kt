package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Procomic :
    Madara(
        "Procomic",
        "https://procomic.pro/",
        "ar",
        dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

        // Preferences
    private val preferences by lazy {
        sourcePref.getSharedPreferences("source_${id}_prefs", 0)
    }

    var customBaseUrl: String
        get() = preferences.getString("custom_base_url", baseUrl) ?: baseUrl
        set(value) = preferences.edit().putString("custom_base_url", value).apply()

    var hidePaidChapters: Boolean
        get() = preferences.getBoolean("hide_paid_chapters", true)
        set(value) = preferences.edit().putBoolean("hide_paid_chapters", value).apply()

    var safeBrowsing: Boolean
        get() = preferences.getBoolean("safe_browsing", true)
        set(value) = preferences.edit().putBoolean("safe_browsing", value).apply()
}
