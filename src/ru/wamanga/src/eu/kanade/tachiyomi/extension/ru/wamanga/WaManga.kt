package eu.kanade.tachiyomi.extension.ru.wamanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class WaManga : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept", "*/*")

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

    override suspend fun getPopularManga(page: Int): MangasPage = catalogParse(client.get(catalogUrl(page, "likes")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = catalogParse(client.get(catalogUrl(page, "updatedAt")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = catalogBuilder(page)

        if (query.isNotBlank()) {
            builder.addQueryParameter("query", query.trim())
        }

        filters.forEach { filter ->
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

        return catalogParse(client.get(builder.build()))
    }

    private fun catalogParse(response: Response): MangasPage {
        val mangas = response.parseSvelte<CatalogDto>().initialMangas
        return MangasPage(mangas.map { it.toSManga(baseUrl) }, mangas.size >= PAGE_SIZE)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manga details + chapters
    // ─────────────────────────────────────────────────────────────────────────

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$baseUrl/${manga.url}/$SVELTE_DATA_SUFFIX").parseSvelte<DetailsDto>().manga

        return SMangaUpdate(
            manga = details.toSManga(baseUrl),
            chapters = details.chapters.map { it.toSChapter(details.mangaUrl) },
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size < 2) return null

        val manga = SManga.create().apply {
            this.url = url.pathSegments.take(2).joinToString("/")
        }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val sameMangas = client.get("$baseUrl/${manga.url}/$SVELTE_DATA_SUFFIX").parseSvelte<DetailsDto>().sameMangas
        return sameMangas.map { it.toSManga(baseUrl) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pages
    // ─────────────────────────────────────────────────────────────────────────

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts
        return "$baseUrl/$type/$slug/glava-$position"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = chapter.url.split("/", limit = 3)
        if (parts.size < 3) throw IllegalStateException("Invalid chapter URL format: ${chapter.url}")
        val (type, slug, position) = parts

        val files = client.get("$baseUrl/$type/$slug/glava-$position/$SVELTE_DATA_SUFFIX")
            .parseSvelte<PagesDto>().chapter.files

        return files
            .sortedBy { it.order }
            .mapIndexed { index, file -> file.toPage(index, baseUrl) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filters
    // ─────────────────────────────────────────────────────────────────────────

    override fun getFilterList(data: JsonElement?): FilterList = defaultFilters()

    companion object {
        private const val PAGE_SIZE = 24
        private const val SVELTE_DATA_SUFFIX = "__data.json?x-sveltekit-invalidated=001"
    }
}
