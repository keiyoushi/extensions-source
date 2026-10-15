package eu.kanade.tachiyomi.extension.en.bbato

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Bbato : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val path = if (page == 1) "/filter?sort=views" else "/filter?sort=views&page=$page"
        return client.get("$baseUrl$path").asJsoup().parseMangasPage()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page == 1) "/updated" else "/updated/page/$page"
        return client.get("$baseUrl$path").asJsoup().parseMangasPage()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/filter".toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

            filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("type[]", it.value) }
            filters.firstInstanceOrNull<GenreFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("genre[]", it.value) }
            filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("status[]", it.value) }
            filters.firstInstanceOrNull<YearFilter>()?.state?.filter { it.state }?.forEach { addQueryParameter("year[]", it.value) }

            filters.firstInstanceOrNull<MinChapterFilter>()?.selectedValue?.takeIf { it.isNotEmpty() }?.let {
                addQueryParameter("minchap", it)
            }

            filters.firstInstanceOrNull<SortFilter>()?.selectedValue?.let {
                addQueryParameter("sort", it)
            }
        }.build()

        return client.get(url).asJsoup().parseMangasPage()
    }

    private fun Document.parseMangasPage(): MangasPage {
        val mangas = select(".original.card-lg .unit").mapNotNull { element ->
            val poster = element.selectFirst("a.poster") ?: return@mapNotNull null
            val title = element.selectFirst(".info > a")?.text() ?: throw Exception("Missing title")
            SManga.create().apply {
                setUrlWithoutDomain(poster.attr("abs:href"))
                this.title = title
                thumbnail_url = poster.selectFirst("img")?.getImageUrl()
            }
        }

        val hasNext = selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true) && !url.host.equals("bbato.com", ignoreCase = true)) return null

        val mangaUrl = when {
            url.encodedPath.startsWith("/manga/") -> url.encodedPath.removeSuffix("/")
            url.encodedPath.startsWith("/read/") -> {
                val slug = url.encodedPath.removePrefix("/read/").substringBefore("/")
                "/manga/$slug"
            }
            else -> return null
        }
        val document = client.get("$baseUrl$mangaUrl").asJsoup()
        return SManga.create().apply {
            setUrlWithoutDomain(mangaUrl)
            title = document.selectFirst("h1[itemprop=name]")?.text() ?: return null
            author = document.select(".meta div:has(span:contains(Author)) a").joinToString { it.text() }
            description = document.selectFirst(".description")?.text()
            genre = document.select(".meta div:has(span:contains(Genres)) a").joinToString { it.text() }
            status = document.selectFirst(".info > p")?.text().toStatus()
            thumbnail_url = document.selectFirst(".poster img")?.getImageUrl()
            initialized = true
        }
    }

    // ============================== Details & Chapters ==============================

    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            val document = client.get(getMangaUrl(manga)).asJsoup()
            SManga.create().apply {
                url = manga.url
                title = document.selectFirst("h1[itemprop=name]")?.text() ?: throw Exception("Missing title")
                author = document.select(".meta div:has(span:contains(Author)) a").joinToString { it.text() }
                description = document.selectFirst(".description")?.text()
                genre = document.select(".meta div:has(span:contains(Genres)) a").joinToString { it.text() }
                status = document.selectFirst(".info > p")?.text().toStatus()
                thumbnail_url = document.selectFirst(".poster img")?.getImageUrl()
                initialized = true
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            val slug = manga.url.removeSuffix("/").substringAfterLast("/")
            val chapterHeaders = headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()
            val responseDto = client.get("$baseUrl/get-chapter-list?slug=$slug", chapterHeaders).parseAs<ChapterListResponse>()
            responseDto.toSChapterList(slug, dateTimeFormat)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun String?.toStatus(): Int = when (this?.trim()?.lowercase(Locale.ENGLISH)) {
        "ongoing", "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on hiatus", "on_hiatus" -> SManga.ON_HIATUS
        "discontinued", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".pages .page:not(.notice-page) img").mapIndexedNotNull { index, img ->
            img.getImageUrl()?.let { Page(index, imageUrl = it) }
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================= Utilities =============================

    private fun Element.getImageUrl(): String? = attr("abs:data-src").ifEmpty { attr("abs:src") }.takeIf { it.isNotEmpty() }
}
