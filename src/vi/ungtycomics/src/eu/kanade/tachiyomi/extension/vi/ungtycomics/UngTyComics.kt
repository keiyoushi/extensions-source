package eu.kanade.tachiyomi.extension.vi.ungtycomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class UngTyComics : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getMangaList(page, sort = "views")

    override suspend fun getLatestUpdates(page: Int) = getMangaList(page, sort = "updated_at")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getMangaList(
        page,
        query,
        filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty(),
        filters.firstInstanceOrNull<SortFilter>()?.value ?: "views",
    )

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!isSiteUrl(url)) return MangasPage(emptyList(), false)
        return when (url.pathSegments.first()) {
            "tim-truyen" -> getMangaList(
                page,
                url.queryParameter("keyword").orEmpty(),
                url.pathSegments.getOrNull(1).orEmpty(),
                url.queryParameter("sort") ?: "updated_at",
            )
            "truyen-hot" -> getMangaList(page, sort = "views")
            "truyen-tranh" -> getMangaList(page, sort = "new")
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!isSiteUrl(url) || url.pathSegments.first() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/truyen/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun isSiteUrl(url: HttpUrl) = url.host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private suspend fun getMangaList(page: Int, query: String = "", genre: String = "", sort: String): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("tim-truyen")
            if (genre.isNotEmpty()) addPathSegment(genre)
            if (query.isNotBlank()) addQueryParameter("keyword", query.trim())
            addQueryParameter("sort", sort)
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".list-comics .item-comics").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".content-title a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".content-image img")?.coverUrl(this.url)
            }
        }
        return MangasPage(mangas, document.selectFirst(".pagination a:has(.fa-angle-double-right)") != null)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document, manga.url), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document, mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst("h1.title-heading")!!.text()
        thumbnail_url = document.selectFirst(".comics-thumbnail img")?.coverUrl(mangaUrl)
        author = document.metaInfo("Tác giả")?.text()?.takeUnless { it.isEmpty() || it == "Đang cập nhật" }
        genre = document.metaInfo("Thể loại")?.select("a")?.joinToString { it.text() }?.ifEmpty { null }
        status = when (document.metaInfo("Trạng thái")?.text()?.lowercase()) {
            "đang tiến hành", "ongoing" -> SManga.ONGOING
            "đã đủ bộ", "hoàn thành", "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        if (status == SManga.COMPLETED) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = MarkdownDescription.Builder()
            .rating(
                document.selectFirst("#averagerate")?.text()?.toDoubleOrNull(),
                max = 5,
                votes = document.selectFirst("#countrate")?.text()?.toIntOrNull(),
            )
            .description(document.selectFirst(".comics-description"))
            .build()
    }

    private fun Document.metaInfo(label: String) = select(".comics-info .meta_item")
        .firstOrNull { it.selectFirst(".meta_label")?.text()?.startsWith(label) == true }
        ?.selectFirst(".meta_info")

    // Covers left on the site's former domains no longer load: the site keeps a copy in its covers folder
    private fun Element.coverUrl(mangaUrl: String): String? {
        val url = absUrl("data-src").ifEmpty { absUrl("src") }.toHttpUrlOrNull() ?: return null
        if (url.host == baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/images/comics/")) return url.toString()
        return "$baseUrl/assets/images/covers/${mangaUrl.substringAfterLast('/')}.jpg"
    }

    private fun chapterListParse(document: Document) = document.select("#chapter_list .item-chapter").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst(".episode-title a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".episode-date > span:not(.view)")?.text(), ZONE)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".content-container img.chapter-img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") }) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        private val ZONE = ZoneId.of("Asia/Ho_Chi_Minh")
    }
}
