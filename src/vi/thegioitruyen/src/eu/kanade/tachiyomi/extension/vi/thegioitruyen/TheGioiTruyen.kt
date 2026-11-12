package eu.kanade.tachiyomi.extension.vi.thegioitruyen

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class TheGioiTruyen : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    // /truyen/ is the full list sorted by latest update
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/truyen/" else "$baseUrl/truyen/page/$page/"
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaList(document)
    }

    // =============================== Latest ===============================

    // No dedicated latest page on this site
    override val supportsLatest get() = false

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toSlug()

        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
                addQueryParameter("post_type", "truyen")
            }.build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            return parseMangaList(document)
        }

        if (genreSlug != null) {
            val url = if (page == 1) {
                "$baseUrl/the-loai/$genreSlug/"
            } else {
                "$baseUrl/the-loai/$genreSlug/page/$page/"
            }
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            return parseMangaList(document)
        }

        return getPopularManga(page)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".tgt-card").mapNotNull { it.toSMangaOrNull() }
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun Element.toSMangaOrNull(): SManga? {
        val link = selectFirst("a.tgt-card-title") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = selectFirst(".tgt-card-thumb img")
                ?.let { it.attr("abs:src").ifEmpty { it.attr("abs:data-src") } }
                ?.takeIf { it.isNotBlank() }
        }
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        url = manga.url
        memo = manga.memo
        title = document.selectFirst("h1.tgt-info-title")?.text()
            ?: document.selectFirst("p.tgt-sidebar-title")!!.text()
        thumbnail_url = document.selectFirst(".tgt-info-thumb img")?.attr("abs:src")
        description = document.selectFirst(".tgt-desc")?.wholeText()?.trim()

        document.select(".tgt-meta-table tr").forEach { row ->
            val label = row.selectFirst("th")?.text()?.lowercase() ?: return@forEach
            val value = row.selectFirst("td") ?: return@forEach
            when {
                "tác giả" in label -> author = value.text().takeIf { it.isNotBlank() }
                "tình trạng" in label -> status = parseStatus(value.text())
                "thể loại" in label -> genre = value.select("a")
                    .joinToString { it.text() }
                    .takeIf { it.isNotBlank() }
            }
        }
    }

    private fun parseStatus(text: String): Int = when (text.lowercase()) {
        "đang phát hành" -> SManga.ONGOING
        "hoàn thành" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select(".tgt-similar-grid .tgt-card").mapNotNull { it.toSMangaOrNull() }
    }

    // ============================= Chapters ===============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select("a.tgt-chapter-item").map { item ->
        SChapter.create().apply {
            setUrlWithoutDomain(item.absUrl("href"))
            name = item.selectFirst(".tgt-chap-name")!!.text()
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".tgt-reader-pages img.tgt-reader-page")
            .mapIndexed { index, img ->
                Page(index, imageUrl = img.attr("abs:src"))
            }
    }

    // ============================= Manga URL ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "truyen") return null
        val mangaUrl = "$baseUrl/truyen/${segments[1]}/"
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return parseMangaDetails(
            document,
            SManga.create().apply {
                setUrlWithoutDomain(mangaUrl)
            },
        )
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/the-loai/", Fetch.DOCUMENT).asJsoup()
        val genres = document.select("a.tgt-genre-card").mapNotNull { card ->
            val name = card.selectFirst(".tgt-genre-name")?.text() ?: return@mapNotNull null
            val slug = card.attr("href")
                .trimEnd('/')
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            GenreDto(name = name, slug = slug)
        }
        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreDto>>())

    @Serializable
    class GenreDto(
        val name: String,
        val slug: String,
    )
}
