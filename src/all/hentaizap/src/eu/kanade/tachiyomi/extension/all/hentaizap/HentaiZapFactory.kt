package eu.kanade.tachiyomi.extension.all.hentaizap

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HentaiZapFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        HentaiZap("en", GalleryAdults.LANGUAGE_ENGLISH),
        HentaiZap("ja", GalleryAdults.LANGUAGE_JAPANESE),
        HentaiZap("es", GalleryAdults.LANGUAGE_SPANISH),
        HentaiZap("fr", GalleryAdults.LANGUAGE_FRENCH),
        HentaiZap("ko", GalleryAdults.LANGUAGE_KOREAN),
        HentaiZap("de", GalleryAdults.LANGUAGE_GERMAN),
        HentaiZap("ru", GalleryAdults.LANGUAGE_RUSSIAN),
        HentaiZap("all", GalleryAdults.LANGUAGE_MULTI),
    )
}
