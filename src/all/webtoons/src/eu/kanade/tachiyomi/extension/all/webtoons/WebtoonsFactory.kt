package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonsFactory : SourceFactory {
    override fun createSources() = listOf(
        Webtoons("en", dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)),
        object : Webtoons("id", dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("id"))) {
            // Override ID as part of the name was removed to be more consiten with other enteries
            override val id: Long = 8749627068478740298
        },
        Webtoons("th", dateFormat = SimpleDateFormat("d MMM yyyy", Locale("th"))),
        Webtoons("es", dateFormat = SimpleDateFormat("d MMMM. yyyy", Locale("es"))),
        Webtoons("fr", dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRENCH)),
        object : Webtoons("zh-Hant", "zh-hant", "zh_TW", SimpleDateFormat("yyyy/MM/dd", Locale.TRADITIONAL_CHINESE)) {
            // Due to lang code getting more specific
            override val id: Long = 2959982438613576472
        },
        Webtoons("de", dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)),
    )
}
