package eu.kanade.tachiyomi.extension.all.imhentai

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class IMHentaiFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        IMHentai("en", GalleryAdults.LANGUAGE_ENGLISH),
        IMHentai("ja", GalleryAdults.LANGUAGE_JAPANESE),
        IMHentai("es", GalleryAdults.LANGUAGE_SPANISH),
        IMHentai("fr", GalleryAdults.LANGUAGE_FRENCH),
        IMHentai("ko", GalleryAdults.LANGUAGE_KOREAN),
        IMHentai("de", GalleryAdults.LANGUAGE_GERMAN),
        IMHentai("ru", GalleryAdults.LANGUAGE_RUSSIAN),
        IMHentai("all", GalleryAdults.LANGUAGE_MULTI),
    )
}
