package eu.kanade.tachiyomi.extension.en.instamanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class InstaManhwa : Madara(
    "InstaManhwa",
    "https://www.instamanhwa.com",
    "en",
    SimpleDateFormat("dd MMMM, yyyy", Locale.US),
) {

    override val supportsLatest: Boolean = false
    override val fetchGenres = false

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest?page=$page", headers)

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?s=$query&page=$page", headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.select("div[id^=manga-chapters-holder]").attr("data-id")
        val cookie = response.headers("").joinToString { it.substringBefore(";") }
        val token = document.select("meta[name=csrf-token]").attr("content")
        return getAjaxChapters(mangaId, cookie, token)
            .select(chapterListSelector())
            .map { chapterFromElement(it) }
    }

    private fun getAjaxChapters(mangaId: String, cookie: String, token: String): Document {
        val headers = headersBuilder()
            .add("Host", baseUrl.substringAfter("https://"))
            .add("Cookie", cookie)
            .build()
        val body = FormBody.Builder()
            .addEncoded("_token", token)
            .addEncoded("action", "manga_get_chapters")
            .addEncoded("manga", mangaId)
            .build()
        return client.newCall(POST("$baseUrl/ajax", headers, body)).execute().asJsoup()
    }

    // Not used
    override fun getFilterList(): FilterList = FilterList()
}
