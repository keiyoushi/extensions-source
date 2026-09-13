package eu.kanade.tachiyomi.extension.tr.sleptmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.select.Elements

@Source
abstract class SleptManga : KeiSource() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/browse?sort=popular&page=$page")

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/browse?sort=latest&page=$page")

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", it.toUriPart())
            }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
                ?.takeIf { it != "all" }
                ?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()
                ?.takeIf { it != "all" }
                ?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<GenreFilterGroup>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genres", it.value) }
        }.build()

        return getMangaList(url.toString())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val type = url.pathSegments.firstOrNull()
        if (type != "manhwa" && type != "manga" && type != "manhua") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/$type/$slug")
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Details & Chapters ===================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get(getMangaUrl(manga))
            .extractNextJs<SeriesDto>()
            ?: throw Exception("Manga detayları bulunamadı")

        val encodedPath = manga.url.removeSuffix("/")

        return SMangaUpdate(
            manga = if (fetchDetails) series.toSManga(baseUrl).apply { url = manga.url } else manga,
            chapters = if (fetchChapters) series.toSChapterList(encodedPath) else chapters,
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.toHttpUrlOrNull()?.pathSegments
            ?: baseUrl.toHttpUrl().resolve(chapter.url.removePrefix("/"))?.pathSegments
            ?: throw Exception("Geçersiz bölüm bağlantısı")

        val slug = segments[segments.size - 2]
        val chapterNumber = segments.last()
        val url = "$baseUrl/api/chapters/$slug/$chapterNumber/images"

        val apiHeaders = headers.newBuilder()
            .add("X-API-Key", API_KEY)
            .add("X-App-Version", APP_VERSION)
            .build()

        val response = client.get(url, apiHeaders)
        val data = response.parseAs<ChapterDataDto>()
        return data.toPageList(baseUrl)
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        GenreFilterGroup(getGenreList()),
    )

    // ============================== Helpers ==============================

    private suspend fun getMangaList(url: String): MangasPage {
        val document = client.get(url).asJsoup()
        val elements = document.select("div.group:has(h3 a)")
        val mangas = parseMangaElements(elements)

        val pagination = document.extractNextJs<PaginationDto>()
        val hasNextPage = when {
            pagination != null -> {
                if (pagination.currentPage >= pagination.totalPages) {
                    false
                } else if (pagination.currentPage == pagination.totalPages - 1) {
                    val nextUrl = url.toHttpUrl().newBuilder()
                        .setQueryParameter("page", pagination.totalPages.toString())
                        .build()
                    val nextDoc = client.get(nextUrl).asJsoup()
                    parseMangaElements(nextDoc.select("div.group:has(h3 a)")).isNotEmpty()
                } else {
                    true
                }
            }
            else -> elements.size >= 24
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaElements(elements: Elements): List<SManga> = elements
        .filterNot { it.selectFirst("h3 a")?.absUrl("href")?.toHttpUrlOrNull()?.pathSegments?.firstOrNull() == "novel" }
        .mapNotNull { element ->
            val link = element.selectFirst("h3 a") ?: return@mapNotNull null
            val href = link.absUrl("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = link.text().takeIf { it.isNotBlank() }
                ?: element.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

    companion object {
        private const val API_KEY = "slept-flutter-xK9mP2wQ7vL4nJ8hB3cF6dR1"
        private const val APP_VERSION = "1.0.5"
    }
}
