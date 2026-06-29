package eu.kanade.tachiyomi.extension.en.mangagg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Element
import rx.Observable

class MangaGG : Madara("MangaGG", "https://mangagg.com", "en") {

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = mangaggLoadMoreRequest(page, true)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "body:not(:has(.no-posts))"

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = mangaggLoadMoreRequest(page, false)

    // ============================= Chapters ==============================
    override val useNewChapterEndpoint = true

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .map { response ->
            val document = response.asJsoup()
            val directChapters = document.select(chapterListSelector())

            if (directChapters.isNotEmpty()) {
                directChapters.map(::chapterFromElement)
            } else {
                val chaptersWrapper = document.select("div[id^=manga-chapters-holder]")
                if (chaptersWrapper.isEmpty()) {
                    emptyList()
                } else {
                    loadPaginatedChapters(document.location().removeSuffix("/"))
                }
            }
        }

    private fun loadPaginatedChapters(mangaUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val xhrRequest = POST("$mangaUrl/ajax/chapters/?t=$page", xhrHeaders)
            val xhrDocument = client.newCall(xhrRequest).execute().use { it.asJsoup() }

            val elements = xhrDocument.select(chapterListSelector())
            if (elements.isEmpty()) break

            chapters.addAll(elements.map(::chapterFromElement))

            if (xhrDocument.selectFirst(".pagination a[data-page=${page + 1}]") == null) break
            page++
        }

        return chapters
    }

    // =============================== Pages ===============================
    override fun imageFromElement(element: Element): String? {
        val url = element.attr("data-src").trim().ifEmpty {
            element.attr("data-lazy-src").trim()
        }.ifEmpty {
            element.attr("data-cfsrc").trim()
        }.ifEmpty {
            element.attr("data-manga-src").trim()
        }.ifEmpty {
            element.attr("src").trim()
        }

        // Jsoup's absUrl fails if a URL has leading spaces.
        // If it starts with http, it is already an absolute URL.
        return if (url.startsWith("http")) url else super.imageFromElement(element)?.trim()
    }

    // ============================= Utilities =============================
    private fun mangaggLoadMoreRequest(page: Int, popular: Boolean): Request {
        val formBody = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page - 1).toString())
            add("template", "madara-core/content/content-search")
            add("vars[s]", "")
            add("vars[orderby]", "meta_value_num")
            add("vars[paged]", "1")
            add("vars[template]", "search")
            add("vars[meta_query][0][relation]", "AND")
            add("vars[meta_query][relation]", "AND")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", if (popular) "_wp_manga_week_views_value" else "_latest_update")
            add("vars[order]", "desc")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
        }.build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }
}
