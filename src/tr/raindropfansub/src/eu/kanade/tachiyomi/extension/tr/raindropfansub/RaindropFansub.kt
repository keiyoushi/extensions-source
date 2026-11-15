package eu.kanade.tachiyomi.extension.tr.raindropfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class RaindropFansub : MangaThemesia() {
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tür) a"

    override fun chapterListParse(document: Document): List<SChapter> {
        // "İlk Bölüm" points to the first chapter, but is often wrong on the site
        // We look at "Son Bölüm" to find the last chapter and sort accordingly
        val chapters = super.chapterListParse(document)

        val lastChapterUrl = document
            .selectFirst("a:has(.epcurlast)")
            ?.attr("href")
            ?.let {
                val dummyChapter = SChapter.create()
                dummyChapter.setUrlWithoutDomain(it)
                dummyChapter.url
            }

        return when (lastChapterUrl) {
            chapters.first().url -> chapters
            chapters.last().url -> chapters.reversed()
            else -> chapters.reversed()
        }
    }
}
