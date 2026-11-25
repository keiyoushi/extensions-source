package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class Bbato : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = client.get("$baseUrl/home", Fetch.DOCUMENT).asJsoup().select("#most-viewed .mt-rank-item").map {
        SManga.create().apply {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.selectFirst(".mt-rank-title")!!.text()
            thumbnail_url = it.selectFirst("img")?.getImageUrl()
        }
    }.let { MangasPage(it, false) }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page == 1) "/updated" else "/updated/page/$page"
        return client.get("$baseUrl$path", Fetch.DOCUMENT).asJsoup().parseMangasPage()
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

        return client.get(url, Fetch.DOCUMENT).asJsoup().parseMangasPage()
    }

    private fun Element.parseMangasPage(): MangasPage {
        val mangas = select(".mt-updates-grid .mt-card").mapNotNull { element ->
            val card = element.selectFirst("a.mt-card-cover") ?: return@mapNotNull null
            val title = element.selectFirst(".mt-card-title")?.text() ?: throw Exception("Missing title")
            SManga.create().apply {
                setUrlWithoutDomain(card.attr("abs:href"))
                this.title = title
                thumbnail_url = card.selectFirst("img")?.getImageUrl()
            }
        }

        val hasNext = selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host.removePrefix("www.")
        if (!host.equals(baseUrl.toHttpUrl().host, ignoreCase = true) && host !in MIRROR_HOSTS && host != MANGABUDDY_HOST) return null

        val segments = url.pathSegments
        val mangaUrl = when {
            host == MANGABUDDY_HOST -> {
                if (segments[0] != "series" || segments.size < 2) return null
                "/manga/${segments[1].substringBeforeLast(".")}"
            }
            url.encodedPath.startsWith("/manga/") -> url.encodedPath.removeSuffix("/")
            url.encodedPath.startsWith("/read/") -> {
                val slug = url.encodedPath.removePrefix("/read/").substringBefore("/")
                "/manga/$slug"
            }
            else -> return null
        }
        return fetchMangaUpdate(
            SManga.create().apply { this.url = mangaUrl },
            emptyList(),
            true,
            false,
        ).manga()
    }

    // ============================== Details & Chapters ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = {
            if (fetchDetails) {
                val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
                SManga.create().apply {
                    url = manga.url
                    title = document.selectFirst("h1[itemprop=name]")?.text() ?: throw Exception("Missing title")
                    author = document.select(".mt-author-line a").joinToString { it.text() }
                    description = (
                        document.selectFirst("#synopsisModal p") ?: document.selectFirst(".mt-comic-synopsis p")
                        )?.text()
                    genre = document.select(".mt-comic-badges a, .mt-hero-genres a").joinToString { it.text() }
                    status = document.selectFirst(".mt-status-tag")?.text().toStatus()
                    thumbnail_url = document.selectFirst(".mt-comic-poster img")?.getImageUrl()
                }
            } else {
                manga
            }
        },
        chapters = {
            if (fetchChapters) {
                val slug = manga.url.removeSuffix("/").substringAfterLast("/")
                val chapterHeaders = headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()
                val responseDto = client.get("$baseUrl/get-chapter-list?slug=$slug", chapterHeaders, Fetch.XHR).parseAs<ChapterListResponse>()
                responseDto.toSChapterList(slug)
            } else {
                chapters
            }
        },
    )

    private fun String?.toStatus(): Int = when (this?.trim()?.lowercase(Locale.ENGLISH)) {
        "ongoing", "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on hiatus", "on_hiatus" -> SManga.ON_HIATUS
        "discontinued", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()

        return document.select(".pages .page:not(.notice-page) img").mapIndexedNotNull { index, img ->
            img.getImageUrl()?.let { Page(index, imageUrl = it) }
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // ============================= Utilities =============================

    private fun Element.getImageUrl(): String? = attr("abs:data-src").ifEmpty { attr("abs:src") }.takeIf { it.isNotEmpty() }

    companion object {
        // Sites with the same series under the same paths
        private val MIRROR_HOSTS = setOf("bbato.com", "xbato.co.uk")

        // Same series under other paths (/series/<slug>.<code>[/chapter-N]), so not a mirror
        private const val MANGABUDDY_HOST = "mangabuddy1.co.uk"
    }
}
