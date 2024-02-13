package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.SourceFactory
import okhttp3.Response

class HNIScantradFactory : SourceFactory {
    override fun createSources() = listOf(HNIScantradFR(), HNIScantradEN())
}

class HNIScantradFR : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "fr", "/lel") {
    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).filter { "/fr/" in it.url }
}

class HNIScantradEN : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "en", "/lel") {
    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).filter { "/en-us/" in it.url }
}
