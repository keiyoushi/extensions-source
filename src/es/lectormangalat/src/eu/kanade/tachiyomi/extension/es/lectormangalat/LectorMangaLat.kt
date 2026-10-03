package eu.kanade.tachiyomi.extension.es.lectormangalat

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class LectorMangaLat : KeiSource() {

    private val apiUrl = "https://$API_HOST/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int) = search(page, "", "views", null)

    override suspend fun getLatestUpdates(page: Int) = search(page, "", null, null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        return search(page, query, sort, status)
    }

    private suspend fun search(page: Int, query: String, sort: String?, status: String?): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            sort?.let { addQueryParameter("sort", it) }
            status?.let { addQueryParameter("estado", it) }
            addQueryParameter("page", page.toString())
        }.build()
        val result = client.get(url).parseAs<SeriesListDto>()
        // The API lists some broken entries without a slug
        return MangasPage(result.data.filter { it.slug.isNotEmpty() }.map { it.toSManga() }, result.hasNextPage)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        if (segments.size < 2 || segments[0] != "comics") return null
        return client.get("$apiUrl/series/${segments[1]}").parseAs<SeriesDetailsDto>().data.toSMangaDetails()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.memo["slug"]!!.stringOrNull}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comics/${chapter.memo["series"]!!.stringOrNull}/capitulo-${chapter.memo["number"]!!.stringOrNull}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = client.get("$apiUrl/series/${manga.memo["slug"]!!.stringOrNull}").parseAs<SeriesDetailsDto>().data
        return SMangaUpdate(series.toSMangaDetails(), series.capitulos.map { it.toSChapter(series.slug) })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/capitulos/${chapter.url}")
        .parseAs<ChapterPagesDto>()
        .data.paginas
        .mapIndexed { i, url -> Page(i, imageUrl = url) }

    private class SortFilter : Filter.Select<String>("Ordenar por", SORTS.map { it.first }.toTypedArray(), 1) {
        val value get() = SORTS[state].second
    }

    private class StatusFilter : Filter.Select<String>("Estado", STATUSES.map { it.first }.toTypedArray()) {
        val value get() = STATUSES[state].second
    }

    companion object {
        private const val API_HOST = "api.zerocomics.net"

        private val SORTS = listOf(
            "Actualización" to null,
            "Popularidad" to "views",
            "Valoración" to "rating",
            "Recientes" to "created_at",
        )

        private val STATUSES = listOf(
            "Todos" to null,
            "En emisión" to "En emisión",
            "Finalizado" to "Finalizado",
        )
    }
}
