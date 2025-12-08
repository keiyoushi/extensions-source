package eu.kanade.tachiyomi.extension.en.mangareadercc

import eu.kanade.tachiyomi.multisrc.paprika.PaprikaAlt
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class MangaReaderIN : PaprikaAlt("MangaReader.in", "https://mangareader.in", "en") {
    override val id = 7388100486112484697

    override fun chapterListSelector() = "li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("script:containsData(var mangaID)")!!
            .data()
            .substringAfter("var mangaID = '")
            .substringBefore("';")
        val mangaTitle = document.select("div.manga-detail h1").text()
        val xhrRequest = GET("$baseUrl/ajax-list-chapter?mangaID=$mangaId", headers)
        client.newCall(xhrRequest).execute().use { xhrResponse ->
            return xhrResponse
                .asJsoup()
                .select(chapterListSelector())
                .map { chapterFromElement(it, mangaTitle) }
        }
    }
}
