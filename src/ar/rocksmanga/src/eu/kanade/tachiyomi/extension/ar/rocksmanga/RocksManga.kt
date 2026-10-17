package eu.kanade.tachiyomi.extension.ar.rocksmanga

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
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class RocksManga : KeiSource() {
    override val supportsFilterFetching = true

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // ============================== Listings ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, sort = "most_viewed")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
        return browse(
            page,
            sort = filters.firstInstanceOrNull<SortFilter>()?.selected,
            query = query,
            type = filters.firstInstanceOrNull<TypeFilter>()?.selected,
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
            include = genres.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.value },
            exclude = genres.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.value },
        )
    }

    private suspend fun browse(
        page: Int,
        sort: String? = null,
        query: String = "",
        type: String? = null,
        status: String? = null,
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
    ): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
            if (!sort.isNullOrEmpty()) {
                addQueryParameter("sort", sort)
                if (sort == "alphabetical") addQueryParameter("direction", "asc")
            }
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            if (!type.isNullOrEmpty()) addQueryParameter("type", type)
            if (!status.isNullOrEmpty()) addQueryParameter("status", status)
            if (include.isNotEmpty()) addQueryParameter("genre", include.joinToString(","))
            if (exclude.isNotEmpty()) addQueryParameter("genre_exclude", exclude.joinToString(","))
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".catalog-grid-compact .unit, main .unit").distinct().map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("li.page-item > a.page-link[rel=next]") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".info > a[href]")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    // ============================ Details & chapters ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = document.extractNextJs<DetailsDto> { it is JsonObject && "readHref" in it }!!.manga
        val chapterList = document.extractNextJs<ChapterListDto> { it is JsonObject && "mangaSlug" in it && "chapters" in it }!!

        return SMangaUpdate(
            details.toSManga(),
            chapterList.chapters.sortedByDescending { it.id }.map { it.toSChapter(chapterList.mangaSlug) },
        )
    }

    // ================================= Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.extractNextJs<ReaderDto> { it is JsonObject && "chapter" in it && "manga" in it }!!
            .chapter.pages.mapIndexed { i, path -> Page(i, imageUrl = "$API_URL$path") }
    }

    // ================================= Filters =================================

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/browse").asJsoup()
        .extractNextJs<GenresDto> { it is JsonObject && "genres" in it && "filters" in it }!!
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
        )
        data?.parseAs<GenresDto>()?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            filters += Filter.Separator()
            filters += GenreFilter(genres.map { Genre(it.name, it.slug) })
        }
        return FilterList(filters)
    }
}

private const val API_URL = "https://api.rocksmanga.com"

private open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

private class SortFilter :
    SelectFilter(
        "ترتيب حسب",
        listOf(
            "آخر التحديثات" to "recently_updated",
            "الأكثر مشاهدة" to "most_viewed",
            "الأعلى تقييماً" to "highest_rated",
            "أبجدياً" to "alphabetical",
            "الأحدث" to "new",
        ),
    )

private class TypeFilter :
    SelectFilter(
        "النوع",
        listOf("الكل" to "", "مانجا" to "manga", "مانهوا" to "manhwa", "مانها" to "manhua", "كوميك" to "comic", "رواية" to "novel"),
    )

private class StatusFilter :
    SelectFilter(
        "الحالة",
        listOf("الكل" to "", "مستمر" to "releasing", "مكتمل" to "completed", "ملغي" to "canceled", "متوقف" to "on_hold"),
    )

private class Genre(name: String, val value: String) : Filter.TriState(name)

private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("التصنيفات", genres)

@Serializable
private class GenresDto(val genres: List<GenreDto>) {
    @Serializable
    class GenreDto(val name: String, val slug: String)
}

@Serializable
private class DetailsDto(val manga: MangaDto) {
    @Serializable
    class MangaDto(
        val slug: String,
        val title: String,
        val alternative_titles: String? = null,
        val synopsis: String? = null,
        val cover_url: String? = null,
        val status: String? = null,
        val authors: List<NameDto> = emptyList(),
        val artists: List<NameDto> = emptyList(),
        val genres: List<NameDto> = emptyList(),
    ) {
        fun toSManga() = SManga.create().apply {
            url = "/manga/$slug"
            title = this@MangaDto.title
            author = authors.joinToString { it.name }.ifEmpty { null }
            artist = artists.joinToString { it.name }.ifEmpty { null }
            description = buildString {
                synopsis?.let { append(it) }
                alternative_titles?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("أسماء أخرى: ", it)
                }
            }.ifEmpty { null }
            genre = genres.joinToString { it.name }.ifEmpty { null }
            thumbnail_url = cover_url?.let { "$API_URL$it" }
            status = when (this@MangaDto.status) {
                "RELEASING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "CANCELED" -> SManga.CANCELLED
                "ON_HOLD" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    @Serializable
    class NameDto(val name: String)
}

@Serializable
private class ChapterListDto(val mangaSlug: String, val chapters: List<ChapterDto>) {
    @Serializable
    class ChapterDto(val id: Int, val number: String, val title: String? = null, val created_at: String? = null) {
        fun toSChapter(slug: String) = SChapter.create().apply {
            url = "/manga/$slug/chapter/$number"
            name = "الفصل $number" + (title?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: "")
            chapter_number = number.toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(created_at)
        }
    }
}

@Serializable
private class ReaderDto(val chapter: ChapterDto) {
    @Serializable
    class ChapterDto(val pages: List<String>)
}
