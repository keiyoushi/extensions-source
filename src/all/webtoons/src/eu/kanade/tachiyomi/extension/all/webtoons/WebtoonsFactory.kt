package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsFactory : SourceFactory {
    override fun createSources() = listOf(
        Webtoons("en"),
        object : Webtoons("id") {
            // Override ID as part of the name was removed to be more consistent with other entries
            override val id: Long = 8749627068478740298
        },
        Webtoons("th"),
        Webtoons("es"),
        Webtoons("fr"),
        object : Webtoons("zh-Hant", "zh-hant", "zh_TW") {
            // Due to lang code getting more specific
            override val id: Long = 2959982438613576472
        },
        Webtoons("de"),
    )
}
