package eu.kanade.tachiyomi.extension.all.yellownote

import eu.kanade.tachiyomi.source.SourceFactory

class YellowNoteSourceFactory : SourceFactory {

    companion object {
        const val BASE_LANGUAGE = "en"
        val SUPPORT_LANGUAGES = setOf(
            "en",
            "es",
            "ko",
            "zh-Hans",
            "zh-Hant",
        )
    }

    override fun createSources() = listOf(
        YellowNote("en", "en"),
        YellowNote("es", "es"),
        YellowNote("ko", "kr"),
        YellowNote("zh-Hans"),
        YellowNote("zh-Hant", "tw"),
    )
}
