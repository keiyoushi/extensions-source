package eu.kanade.tachiyomi.extension.tr.norascans

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class NoraScans : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/seri/".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        }.build()
        return parseMangasPage(client.get(url))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "manga")
            .apply { if (page > 1) addQueryParameter("paged", page.toString()) }
            .build()
        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-item-grid").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h2 a.uk-link-heading")!!
                title = link.text()
                setUrlWithoutDomain(link.attr("href"))
                thumbnail_url = element.selectFirst("img.image-3-4")?.attr("abs:src")
            }
        }
        val hasNext = document.selectFirst("a[href*='/seri/page/${getPageNumber(response.request.url) + 1}/']") != null
        return MangasPage(mangas, hasNext)
    }

    private fun getPageNumber(url: okhttp3.HttpUrl): Int {
        val segments = url.pathSegments
        val pageIdx = segments.indexOf("page")
        return if (pageIdx >= 0 && pageIdx + 1 < segments.size) {
            segments[pageIdx + 1].toIntOrNull() ?: 1
        } else {
            1
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val updatedManga = SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            description = document.selectFirst("div#manga-description")?.text()
            initialized = true
        }

        val chapterList = document.select("a[href*=/bolum-]").mapNotNull { element ->
            val href = element.attr("href")
            val num = href.substringAfterLast("/bolum-").trimEnd('/').toFloatOrNull() ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = "Bölüm $num"
                chapter_number = num
            }
        }.distinctBy { it.url }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("img[src*=init-manga]").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url
}
