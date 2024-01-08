package eu.kanade.tachiyomi.extension.all.imhentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class IMHentaiFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        IMHentai("en", IMHentai.LANGUAGE_ENGLISH),
        IMHentai("ja", IMHentai.LANGUAGE_JAPANESE),
        IMHentai("es", IMHentai.LANGUAGE_SPANISH),
        IMHentai("fr", IMHentai.LANGUAGE_FRENCH),
        IMHentai("ko", IMHentai.LANGUAGE_KOREAN),
        IMHentai("de", IMHentai.LANGUAGE_GERMAN),
        IMHentai("ru", IMHentai.LANGUAGE_RUSSIAN),
    )
}
