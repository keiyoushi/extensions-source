package eu.kanade.tachiyomi.extension.en.kappabeast

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

@Source
abstract class KappaBeast : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val cdnUrl get() = "https://strapi.$domain"
    private val apiUrl get() = "$cdnUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangas(page, "", "", "", "", "")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangas(page, "", "", "", "", "updatedAt:desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.value.orEmpty()
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value.orEmpty()
        return fetchMangas(page, query, genre, status, type, sort)
    }

    private suspend fun fetchMangas(
        page: Int,
        query: String,
        genre: String,
        status: String,
        type: String,
        sort: String,
    ): MangasPage {
        fun Builder.addParam(param: String, value: String) = value.takeIf { it.isNotBlank() }?.let { addQueryParameter(param, it) }

        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter($$"filters[title][$containsi]", query)

            addQueryParameter("pagination[page]", page.toString())
            addQueryParameter("pagination[pageSize]", "20")
            addQueryParameter("populate[media][populate]", "*")
            addQueryParameter("populate[category][fields][0]", "name")

            addParam($$"filters[category][name][$eq]", genre)
            addParam($$"filters[manga_status][$eq]", status)
            addParam($$"filters[type][$eq]", type)
            addParam("sort[0]", sort)
        }.build()

        val result = client.get(url).parseAs<SearchResponse>()
        return MangasPage(result.data.map { it.toSManga(cdnUrl) }, result.meta.pagination.hasNextPage())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together"),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        SortFilter(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.first()

        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[slug][$eq]", slug)
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("populate[chapters][fields][0]", "number")
            .addQueryParameter("populate[chapters][fields][1]", "title")
            .addQueryParameter("populate[chapters][fields][2]", "createdAt")
            .addQueryParameter("populate[chapters][sort][0]", "number:desc")
            .addQueryParameter("pagination[pageSize]", "1")
            .build()

        val data = client.get(url).parseAs<SearchResponse>().data.first()
        return SMangaUpdate(data.toSManga(cdnUrl), data.toSChapters())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val api = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[slug][$eq]", slug)
            .addQueryParameter("populate[media][populate]", "*")
            .addQueryParameter("populate[category][fields][0]", "name")
            .addQueryParameter("pagination[pageSize]", "1")
            .build()

        val data = client.get(api).parseAs<SearchResponse>().data.firstOrNull() ?: return null
        return data.toSManga(cdnUrl).apply { initialized = true }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val parts = "$baseUrl/${chapter.url}".toHttpUrl()
        val documentId = parts.fragment
        val chapterNum = parts.pathSegments[1]
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter($$"filters[manga][documentId][$eq]", documentId)
            .addQueryParameter($$"filters[number][$eq]", chapterNum)
            .addQueryParameter("pagination[pageSize]", "1")
            .build()

        val html = client.get(url).parseAs<ChapterPageResponse>().data.firstOrNull()?.htmlContent
            ?: return emptyList()
        return Jsoup.parseBodyFragment(html, baseUrl).select("div.separator > a").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("href").toHttpUrl().newBuilder().setPathSegment(4, "s0").build().toString())
        }
    }
}
