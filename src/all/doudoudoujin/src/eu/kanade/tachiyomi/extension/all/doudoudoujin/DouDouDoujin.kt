package eu.kanade.tachiyomi.extension.all.doudoudoujin

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class DouDouDoujin : KeiSource() {

    // The site lists its series newest first, with no other order
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The list goes by cursor and the site has no search: follow the cursor to the end and filter here
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.id
        val series = mutableListOf<SeriesDto>()
        var cursor: String? = null
        do {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("series.data")
                .apply {
                    genre?.let { addQueryParameter("genre", it) }
                    cursor?.let { addQueryParameter("after", it) }
                }
                .build()
            val list = fetchData<SeriesListDto>(url).series
            series += list.items
            cursor = list.next
        } while (cursor != null)

        return MangasPage(series.filter { it.matches(query.trim()) }.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        val seriesPath = when (url.pathSegments[0]) {
            "series" -> "/series/${url.pathSegments[1]}"
            // A chapter only knows its series by id, which the site redirects to the series' own address
            "mangas" -> {
                val seriesId = fetchData<ChapterSeriesDto>(dataUrl("/mangas/${url.pathSegments[1]}")).manga.seriesId
                client.get("$baseUrl/series/$seriesId", Fetch.DOCUMENT).use { it.request.url.encodedPath }
            }
            else -> return null
        }

        return fetchData<SeriesDetailsDto>(dataUrl(seriesPath)).series.toSManga().apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = fetchData<SeriesDetailsDto>(dataUrl(manga.url))
        val chapterList = details.mangas
            .sortedByDescending { it.number }
            .map { it.toSChapter() }

        return SMangaUpdate(details.series.toSManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchData<MangaPagesDto>(dataUrl(chapter.url)).manga.pages
        .sortedBy { it.pageNumber }
        .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // React Router serves each page's data at the same address with ".data" added
    private fun dataUrl(path: String) = "$baseUrl$path.data".toHttpUrl()

    private suspend inline fun <reified T> fetchData(url: HttpUrl): T = client.get(url, Fetch.XHR).parseAs<JsonArray>()
        .decodeTurboStream()
        .jsonObject.values.last()
        .jsonObject["data"]!!
        .parseAs<T>()

    // React Router's format (turbo-stream): one flat array, where objects and arrays give the indexes of their values
    // and objects also those of their keys, negative indexes being null or undefined
    private fun JsonArray.decodeTurboStream(): JsonElement {
        val decoded = HashMap<Int, JsonElement>()
        fun decode(index: Int): JsonElement {
            if (index < 0) return JsonNull
            return decoded.getOrPut(index) {
                when (val value = this[index]) {
                    is JsonArray -> JsonArray(value.map { decode(it.jsonPrimitive.int) })
                    is JsonObject -> JsonObject(value.entries.associate { (key, item) -> this[key.drop(1).toInt()].jsonPrimitive.content to decode(item.jsonPrimitive.int) })
                    is JsonPrimitive -> value
                }
            }
        }
        return decode(0)
    }
}
