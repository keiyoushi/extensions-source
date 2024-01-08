package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import rx.Observable

class DynastySeries : DynastyScans() {

    override val name = "Dynasty-Series"

    override val searchPrefix = "series"

    override fun popularMangaInitialUrl() = "$baseUrl/series?view=cover"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?q=$query&classes%5B%5D=Series&sort=&page=$page", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("manga:chapters:")) {
            val seriesName = Regex("""manga:chapters:(.*?)_ch[0-9_]+""").matchEntire(query)?.groups?.get(1)?.value
            if (seriesName != null) {
                return super.fetchSearchManga(page, "manga:$searchPrefix:$seriesName", filters)
            }
        }
        return super.fetchSearchManga(page, query, filters)
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
