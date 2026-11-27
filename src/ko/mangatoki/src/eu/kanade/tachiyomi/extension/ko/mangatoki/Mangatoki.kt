package eu.kanade.tachiyomi.extension.ko.mangatoki

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Mangatoki : KeiSource() {

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegments("api/manga").build()

    // Images are on the img subdomain of the site's domain
    private val imageUrl get() = "https://img.${baseUrl.toHttpUrl().host.removePrefix("mg.")}".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(VIEW_SORT)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val text = query.trim()
        val url = apiUrl.newBuilder()
            .apply {
                if (text.isEmpty()) return@apply
                val (type, q) = genreTag(text) ?: (filters.firstInstanceOrNull<SearchTypeFilter>()?.value ?: "title").let { type ->
                    // Tags are stored with underscores between words
                    type to if (type == "tag") text.replace(' ', '_') else text
                }
                addPathSegment("search")
                addQueryParameter("type", type)
                addQueryParameter("q", q)
            }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.value ?: SORTS.first().second)
            .build()
        return client.get(url, Fetch.XHR).parseAs<MangaListDto>().toMangasPage(page, imageUrl)
    }

    // A genre as the details show it, such as "big breasts ♀", is the tag female:big_breasts
    private fun genreTag(text: String): Pair<String, String>? {
        val namespace = when {
            text.endsWith(" ♀") -> "female"
            text.endsWith(" ♂") -> "male"
            else -> return null
        }
        return "tag" to "$namespace:${text.dropLast(2).trim().replace(' ', '_')}"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = fetchManga(manga.url)
        return SMangaUpdate(dto.toDetails(imageUrl), listOf(dto.toChapter()))
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchManga(chapter.url).toPages(imageUrl)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SearchTypeFilter(),
        SortFilter(),
    )

    private suspend fun fetchManga(id: String) = client.get(apiUrl.newBuilder().addPathSegment(id).build(), Fetch.XHR).parseAs<MangaDto>()
}
