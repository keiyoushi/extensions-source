package eu.kanade.tachiyomi.extension.all.hentaienvy

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HentaiEnvyFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HentaiEnvy("en", GalleryAdults.LANGUAGE_ENGLISH),
        HentaiEnvy("ja", GalleryAdults.LANGUAGE_JAPANESE),
        HentaiEnvy("es", GalleryAdults.LANGUAGE_SPANISH),
        HentaiEnvy("fr", GalleryAdults.LANGUAGE_FRENCH),
        HentaiEnvy("ko", GalleryAdults.LANGUAGE_KOREAN),
        HentaiEnvy("de", GalleryAdults.LANGUAGE_GERMAN),
        HentaiEnvy("ru", GalleryAdults.LANGUAGE_RUSSIAN),
        HentaiEnvy("all", GalleryAdults.LANGUAGE_MULTI),
    )
}
