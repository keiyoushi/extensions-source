package eu.kanade.tachiyomi.extension.en.pornhwa18

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class Pornhwa18 : KeiSource() {

    override suspend fun getPopularManga(page: Int) = listing(baseUrl.toHttpUrl().newBuilder().addPathSegment("popular"), page)

    override suspend fun getLatestUpdates(page: Int) = listing(baseUrl.toHttpUrl().newBuilder(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = listing(
        baseUrl.toHttpUrl().newBuilder().addPathSegment("search").addPathSegment(query.trim()),
        page,
    )

    // Every listing loads more by returning all previous pages again, plus the next one
    private suspend fun listing(url: HttpUrl.Builder, page: Int): MangasPage {
        url.addPathSegment("q-data.json")
        if (page > 1) url.addQueryParameter("page", page.toString())
        val all = client.get(url.build()).qwikLoader().parseAs<List<SeriesDto>>()
        val mangas = all.drop((page - 1) * PAGE_SIZE).filter { it.slug.isNotBlank() }.map { it.toSManga() }
        return MangasPage(mangas, all.size >= page * PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotBlank)
        if (segments.size < 2 || segments[0] != "comic") return null
        return fetchSeries(segments[1]).toSMangaDetails()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comic/${manga.memo["slug"]!!.jsonPrimitive.content}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comic/${chapter.memo["slug"]!!.jsonPrimitive.content}/chapter-${chapter.memo["chapter"]!!.jsonPrimitive.content}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchSeries(manga.memo["slug"]!!.jsonPrimitive.content)
        return SMangaUpdate(series.toSMangaDetails(), series.toSChapters())
    }

    private suspend fun fetchSeries(slug: String) = client.get("$baseUrl/comic/$slug/q-data.json").qwikLoader().parseAs<SeriesDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("${getChapterUrl(chapter)}q-data.json")
        .qwikLoader()
        .parseAs<ReaderDto>()
        .data.chapters.first().images
        .entries.sortedBy { it.key.toInt() }
        .mapIndexed { i, (_, image) -> Page(i, imageUrl = image.src) }

    // Qwik City route loader data: every object/array value is a base-36 index into `_objs`
    private fun Response.qwikLoader(): JsonElement {
        val root = parseAs<JsonObject>()
        val objs = root["_objs"]!!.jsonArray

        fun resolve(ref: JsonElement): JsonElement = when (val value = objs[ref.jsonPrimitive.content.toInt(36)]) {
            is JsonObject -> JsonObject(value.mapValues { resolve(it.value) })
            is JsonArray -> JsonArray(value.map(::resolve))
            is JsonNull -> JsonNull
            is JsonPrimitive -> when {
                !value.isString -> value
                value.content == UNDEFINED -> JsonNull
                // Type-tagged strings (dates, escaped strings) start with a control character
                value.content.firstOrNull()?.let { it < ' ' } == true -> JsonPrimitive(value.content.substring(1))
                else -> value
            }
        }

        return resolve(root["_entry"]!!).jsonObject["loaders"]!!.jsonObject.values.first { it !is JsonNull }
    }

    companion object {
        private const val PAGE_SIZE = 18
        private const val UNDEFINED = "\u0001"
    }
}
