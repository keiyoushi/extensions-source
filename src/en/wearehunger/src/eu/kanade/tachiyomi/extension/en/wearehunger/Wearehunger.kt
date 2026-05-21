package eu.kanade.tachiyomi.extension.en.wearehunger

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

class Wearehunger :
    Madara(
        "KokoMangas",
        "https://kokomangas.com",
        "en",
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
    override val versionId = 2
    override fun parseGenres(document: Document): List<Genre> = document.select(".pls-filter-bar a[href*=/manga-genre/]").map {
        Genre(
            name = it.text(),
            id = it.absUrl("href").toHttpUrl().pathSegments[1],
        )
    }
}
