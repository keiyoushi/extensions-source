package eu.kanade.tachiyomi.extension.vi.metruyen18

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MeTruyen18 : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val supportsRelatedMangas = false
    override val genreDirectory = "the-loai"
    override fun chapterListSelector() = "#chapterlist li.a-h"
    override val chapterDateSelector = ".chapter-time"
    override val mangaDetailsSelectorDescription = ".panel-story-description .dsct"
    override val mangaDetailsSelectorGenre = "div.post-content_item:has(div.summary-heading h5:contains(Thể loại)) div.summary-content a"

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return document.select("a[href*='/$genreDirectory/']").mapNotNull { element ->
            val href = element.absUrl("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath
            val slug = path.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return@mapNotNull null
            GenreRoute(element.text(), slug, path)
        }.distinctBy(GenreRoute::slug).toJsonElement()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "-views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, "-updated_at")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("tim-kiem")
            .addQueryParameter("filter[name]", query)
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaList(client.get(url).asJsoup(), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "truyen") return null
        val path = url.encodedPath.trimEnd('/')
        val document = client.get(url).asJsoup()
        return parseDetails(document, path, path).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val path = manga.url
        return SMangaUpdate(
            manga = parseDetails(document, manga.url, path),
            chapters = parseChapterList(document, path),
        )
    }

    private suspend fun getMangaList(page: Int, sort: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("danh-sach")
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .build()
        return parseMangaList(client.get(url).asJsoup(), page)
    }

    private fun parseMangaList(document: Document, page: Int): MangasPage {
        val mangas = document.select(".bsx-item").mapNotNull { element ->
            val link = element.selectFirst(".tt a") ?: return@mapNotNull null
            val href = link.absUrl("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath.trimEnd('/')
            SManga.create().apply {
                url = path
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")?.takeIf(String::isNotBlank)
                    ?: element.selectFirst("img")?.absUrl("src")
                memo = mangaMemo(path, emptyList())
            }
        }
        return MangasPage(mangas, document.selectFirst("a[href*='page=${page + 1}']") != null)
    }
}
