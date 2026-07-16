package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class WaManga : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "*/*")

    // ─────────────────────────────────────────────────────────────────────────
    // Catalog
    // ─────────────────────────────────────────────────────────────────────────

    private fun catalogBuilder(page: Int) = "$baseUrl/catalog/__data.json".toHttpUrl().newBuilder()
        .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
        .addQueryParameter("limit", PAGE_SIZE.toString())
        .addQueryParameter("x-sveltekit-invalidated", "001")

    private fun catalogUrl(page: Int, sortKey: String, sortDescending: Boolean = true) = catalogBuilder(page)
        .addQueryParameter("sortKey", sortKey)
        .addQueryParameter("sortDescending", sortDescending.toString())
        .build()

    override fun popularMangaRequest(page: Int): Request = GET(catalogUrl(page, "likes"), headers)

    override fun popularMangaParse(response: Response): MangasPage = catalogParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET(catalogUrl(page, "updatedAt"), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = catalogParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = catalogBuilder(page)

        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query.trim())
        }

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderByFilter -> {
                    builder.addQueryParameter("sortKey", filter.toKey())
                    builder.addQueryParameter("sortDescending", filter.toDescending().toString())
                }

                is TypeGroup -> {
                    filter.state.filter { it.state }.forEach {
                        builder.addQueryParameter("types", it.id)
                    }
                }

                is StatusGroup -> {
                    filter.state.filter { it.state }.forEach {
                        builder.addQueryParameter("statuses", it.id)
                    }
                }

                is TranslationStatusGroup -> {
                    filter.state.filter { it.state }.forEach {
                        builder.addQueryParameter("translationStatuses", it.id)
                    }
                }

                is PegiGroup -> {
                    filter.state.filter { it.state }.forEach {
                        builder.addQueryParameter("pegiRatings", it.id)
                    }
                }

                is YearGroup -> {
                    filter.state.firstInstanceOrNull<YearFromFilter>()?.toQueryValue()?.let {
                        builder.addQueryParameter("releaseYearFrom", it)
                    }
                    filter.state.firstInstanceOrNull<YearToFilter>()?.toQueryValue()?.let {
                        builder.addQueryParameter("releaseYearTo", it)
                    }
                }

                else -> {}
            }
        }

        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = catalogParse(response)

    private fun catalogParse(response: Response): MangasPage {
        val mangas = response.parseSvelte<CatalogDto>().initialMangas
        return MangasPage(mangas.map { it.toSManga(baseUrl) }, mangas.size >= PAGE_SIZE)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manga details
    // ─────────────────────────────────────────────────────────────────────────

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/${manga.url}/$SVELTE_DATA_SUFFIX", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseSvelte<DetailsDto>().manga.toSManga(baseUrl)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    // ─────────────────────────────────────────────────────────────────────────
    // Chapters
    // ─────────────────────────────────────────────────────────────────────────

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseSvelte<DetailsDto>().manga

        return manga.chapters.map { it.toSChapter(manga.mangaUrl) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts
        return "$baseUrl/$type/$slug/glava-$position"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pages
    // ─────────────────────────────────────────────────────────────────────────

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts
        return GET("$baseUrl/$type/$slug/glava-$position/$SVELTE_DATA_SUFFIX", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val files = response.parseSvelte<PagesDto>().chapter.files

        return files
            .sortedBy { it.order }
            .mapIndexed { index, file -> file.toPage(index, baseUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ─────────────────────────────────────────────────────────────────────────
    // Filters
    // ─────────────────────────────────────────────────────────────────────────

    override fun getFilterList(): FilterList = defaultFilters()

    companion object {
        private const val PAGE_SIZE = 24
        private const val SVELTE_DATA_SUFFIX = "__data.json?x-sveltekit-invalidated=001"
    }
}
