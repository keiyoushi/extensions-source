package eu.kanade.tachiyomi.extension.pt.cerisescans

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class CeriseScan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = getComicList(page, sort = "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getComicList(page, sort = "recent")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getComicList(page, query = query, filters = filters)

    private suspend fun getComicList(
        page: Int,
        sort: String? = null,
        query: String = "",
        filters: FilterList = FilterList(),
    ): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())

        if (sort != null) {
            url.addQueryParameter("sort", sort)
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.filterIsInstance<SelectFilter>().forEach { filter ->
            filter.selected?.let { url.addQueryParameter(filter.parameter, it) }
        }

        return client.get(url.build()).parseAs<ComicListDto>().toMangasPage(page, baseUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val fragment = url.fragment ?: return null
        val slugOrId = MANGA_FRAGMENT_REGEX.matchEntire(fragment)?.groupValues?.get(1) ?: return null
        return getComic(slugOrId).toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = getComic(manga.url.toSlug())
        return SMangaUpdate(comic.toSManga(baseUrl), comic.lastChapters.map { it.toSChapter() })
    }

    private suspend fun getComic(slugOrId: String): ComicDto {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addPathSegment(slugOrId)
            .build()
        return client.get(url).parseAs<ComicDto>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/api/chapter-images".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapter.url)
            .build()
        val siteUrl = baseUrl.toHttpUrl()
        return client.get(url).parseAs<List<String>>().mapIndexed { index, path ->
            Page(index, imageUrl = siteUrl.resolve(path)!!.toString())
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SelectFilter("Gênero", "genre", GENRES.map { it to it }),
        SelectFilter("Status", "status", STATUSES),
    )

    private class SelectFilter(
        name: String,
        val parameter: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(name, arrayOf("Todos") + options.map { it.first }) {
        val selected: String? get() = if (state == 0) null else options[state - 1].second
    }

    override fun getHomeUrl(): String = "$baseUrl/#/library"

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/#/comic/${manga.url.toSlug()}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/#/read/${chapter.url}"

    // Library entries saved by the old Madara-based source have "/manga/<slug>/" as their URL.
    private fun String.toSlug() = removePrefix("/manga/").removeSuffix("/")

    companion object {
        private const val PAGE_SIZE = 50

        private val MANGA_FRAGMENT_REGEX = Regex("""/(?:comic|obra)/([^/?]+)""")

        private val STATUSES = listOf(
            "Em Andamento" to "ongoing",
            "Finalizado" to "completed",
            "Hiato" to "hiatus",
            "Dropado" to "dropped",
            "Inativo" to "inactive",
            "Cancelado" to "cancelled",
        )

        private val GENRES = listOf(
            "Ação",
            "Adulto",
            "Apocalíptico",
            "Artes Marciais",
            "Aventura",
            "BL",
            "Comédia",
            "Drama",
            "Escolar",
            "Esportes",
            "Fantasia",
            "Ficção Científica",
            "Gore",
            "Harem",
            "Harém",
            "Histórico",
            "Horror",
            "Isekai",
            "Josei",
            "Magia",
            "Mistério",
            "Psicológico",
            "Reencarnação",
            "Regressão",
            "Romance",
            "Sci-fi",
            "Seinen",
            "Shoujo",
            "Shounen",
            "Slice of Life",
            "Smut",
            "Sobrenatural",
            "Suspense",
            "Tragédia",
            "Vida Escolar",
            "Vingança",
            "Webtoon",
            "Yaoi",
            "Yuri",
        )
    }
}
