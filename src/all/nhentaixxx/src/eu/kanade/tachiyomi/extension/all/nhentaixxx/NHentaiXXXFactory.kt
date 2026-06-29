package eu.kanade.tachiyomi.extension.all.nhentaixxx

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NHentaiXXXFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NHentaiXXX("en", GalleryAdults.LANGUAGE_ENGLISH),
        NHentaiXXX("ja", GalleryAdults.LANGUAGE_JAPANESE),
        NHentaiXXX("zh", GalleryAdults.LANGUAGE_CHINESE),
        NHentaiXXX("all", GalleryAdults.LANGUAGE_MULTI),
    )
}
