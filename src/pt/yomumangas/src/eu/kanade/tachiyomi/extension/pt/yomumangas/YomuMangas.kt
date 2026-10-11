package eu.kanade.tachiyomi.extension.pt.yomumangas
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class YomuMangas : KeiSource() {

    private val apiUrl = "https://api.yomumangas.com"

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int) = client.get(
        "$apiUrl/home/updates?page=$page",
    ).parseAs<LatestUpdatesResponse>().let { mangas ->
        MangasPage(mangas.medias.map { it.toSManga() }, page < 20)
    }

    // ============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1) ?: return null
        if (!id.all(Char::isDigit)) return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = id },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search/medias".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("type", filter.toUriPart())
                is StatusFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("status", filter.toUriPart())
                is NsfwFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("nsfw", filter.toUriPart())
                is GenreFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        url.addQueryParameter("genres", selected.joinToString(","))
                    }
                }
                is TagFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        url.addQueryParameter("tags", selected.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        val dto = client.get(url.build())
            .parseAs<SearchResponse>()
        return MangasPage(
            dto.mangas.map { it.toSManga() },
            page < dto.pages,
        )
    }

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String {
        val (id, slug) = manga.url.split("#", limit = 2)
        return "$baseUrl/mangas/$id/$slug"
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = manga.url.substringBefore("#")
        val slug = manga.url.substringAfter("#", "")

        val updatedManga = async {
            if (fetchDetails) {
                client.get("$apiUrl/mangas/$id")
                    .parseAs<MangaDetailsResponse>()
                    .manga
                    .toSManga()
            } else {
                manga
            }
        }
        val updatedChapters = async {
            if (fetchChapters) {
                client.get("$apiUrl/mangas/$id/chapters")
                    .parseAs<ChaptersResponse>()
                    .chapters
                    .map { it.toSChapter(id, slug) }
                    .reversed()
            } else {
                chapters
            }
        }

        SMangaUpdate(
            updatedManga.await(),
            updatedChapters.await(),
        )
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = client.get(
            getChapterUrl(chapter),
        ).body.string()

        val pages = URI_REGEX.findAll(html).mapIndexed { index, matchResult ->
            Page(index, imageUrl = matchResult.value.replaceB2Uri())
        }.toList()

        if (pages.isEmpty()) {
            throw Exception("Nenhuma página encontrada. O layout do site pode ter mudado.")
        }

        return pages
    }

    // ============================== Filters ==============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = coroutineScope {
        val tags = async { client.get("$apiUrl/tags").parseAs<FilterData>().tags }
        val genres = async { client.get("$apiUrl/genres").parseAs<FilterData>().genres }
        FilterData(tags.await(), genres.await())
    }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOf(
            TypeFilter(),
            StatusFilter(),
            NsfwFilter(),
            Filter.Separator(),
        ) + buildList {
            data?.parseAs<FilterData>()?.let { filters ->

                filters.genres.takeIf { it.isNotEmpty() }?.let {
                    add(GenreFilter(it.map { Genre(it.name, it.id) }))
                }

                filters.tags.takeIf { it.isNotEmpty() }?.let {
                    add(Filter.Separator())
                    add(TagFilter(it.map { Tag(it.name, it.id) }))
                }
            }
        },
    )

    // ============================= Utilities =============================
    companion object {
        private val URI_REGEX = """b2://chapters/[^"\\]+""".toRegex()
    }
}
