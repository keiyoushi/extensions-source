package eu.kanade.tachiyomi.extension.all.foamgirl

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FoamGirl : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ======================================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/page/$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".update_area .i_list").map { element ->
            SManga.create().apply {
                thumbnail_url = element.select("img").attr("data-original")
                title = element.select("a.meta-title").text()
                setUrlWithoutDomain(element.select("a").attr("href"))
                initialized = true
            }
        }
        val hasNextPage = document.selectFirst("a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ======================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("page")
            addPathSegment("$page")
            addQueryParameter("post_type", "post")
            addQueryParameter("s", query)
        }.build()

        return parseMangaList(client.get(url).asJsoup())
    }

    // ============================== Details ======================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(document.select("link[rel=canonical]").attr("abs:href"))
            chapter_number = 0F
            name = "GALLERY"
            date_upload = DATE_FORMAT.tryParseDate(document.select("span.image-info-time").text().substring(1))
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    // ============================== Pages ======================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val allPages = mutableListOf<Page>()
        var document = client.get(getChapterUrl(chapter)).asJsoup()
        var pageIndex = 0

        while (true) {
            document.select(".imageclick-imgbox").forEach { element ->
                allPages.add(Page(pageIndex++, imageUrl = element.absUrl("href")))
            }

            val nextPageUrl = document.selectFirst(".page-numbers[title=Next page]")
                ?.absUrl("href")
                ?.takeIf { HAS_NEXT_PAGE_REGEX in it }
                ?: break

            document = client.get(nextPageUrl).asJsoup()
        }

        return allPages
    }

    companion object {
        private val HAS_NEXT_PAGE_REGEX = """(\d+_\d+)""".toRegex()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d", Locale.ENGLISH)
    }
}
