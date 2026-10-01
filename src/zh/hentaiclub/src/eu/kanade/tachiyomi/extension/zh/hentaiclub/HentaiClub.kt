package eu.kanade.tachiyomi.extension.zh.hentaiclub

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class HentaiClub : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return parseMangaList(url)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val searchUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(query.trim())
                .build()
                .toString()
            val url = if (page > 1) "$searchUrl/$page/" else "$searchUrl/"
            return parseMangaList(url)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()

        if (tagFilter != null && tagFilter.state.isNotBlank()) {
            val tag = tagFilter.state.trim()
            val base = "$baseUrl/tag/$tag/"
            val url = if (page > 1) "$base$page/" else base
            return parseMangaList(url)
        }

        if (sortFilter != null && sortFilter.state > 0) {
            val sortValue = sortFilter.getValue()
            return parseMangaList("$baseUrl/sort/$sortValue.html")
        }

        return getPopularManga(page)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val chapterList = listOf(
            SChapter.create().apply {
                name = "章节 1"
                setUrlWithoutDomain(manga.url)
            },
        )

        if (!fetchDetails) return SMangaUpdate(manga, chapterList)

        val response = client.get(getMangaUrl(manga))
        val encodedPath = response.request.url.encodedPath
        val document = response.asJsoup()
        val contentEl = document.selectFirst(".content")

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = document.title().substringBefore(" - 绅士会所")

            val firstImage = contentEl?.selectFirst("div.post-item[data-src]")
            thumbnail_url = firstImage?.absUrl("data-src")

            val tagLinks = contentEl?.select("a[href*=/tag/]") ?: emptyList()
            author = tagLinks.firstOrNull()?.text()
            genre = tagLinks.joinToString { it.text() }

            val viewsMatch = VIEWS_REGEX.find(contentEl?.text() ?: "")
            description = viewsMatch?.let { "浏览量：${it.groupValues[1]}次" }

            status = when {
                encodedPath.contains("/r18/") -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.post-item[data-src]").mapIndexed { idx, el ->
            Page(idx, imageUrl = el.absUrl("data-src"))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TagFilter(),
    )

    // ============================= Utilities =============================

    private suspend fun parseMangaList(url: String): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select("div.item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.item-link")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst(".item-link-text")!!.text()

                val img = element.selectFirst("img.item-img")
                thumbnail_url = img?.absUrl("data-original")?.ifEmpty { img.absUrl("src") }
            }
        }

        val hasNextPage = mangas.size >= 24

        return MangasPage(mangas, hasNextPage)
    }

    companion object {
        private val VIEWS_REGEX = Regex("浏览[：:]\\s*(\\d+)次")
    }
}
