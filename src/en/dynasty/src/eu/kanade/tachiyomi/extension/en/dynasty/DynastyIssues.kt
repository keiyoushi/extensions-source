package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document

class DynastyIssues : DynastyScans() {

    override val name = "Dynasty-Issues"

    override val searchPrefix = "issues"

    override fun popularMangaInitialUrl() = "$baseUrl/issues?view=cover"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Issue&sort=&page=$page", headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + document.select("div.span2 > img").attr("src")
        parseHeader(document, manga)
        parseGenres(document, manga)
        parseDescription(document, manga)
        return manga
    }
}
