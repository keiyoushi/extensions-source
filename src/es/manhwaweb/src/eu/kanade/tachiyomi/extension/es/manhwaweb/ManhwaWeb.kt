package eu.kanade.tachiyomi.extension.es.manhwaweb

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ManhwaWeb : KeiSource() {

    private val apiUrl = "https://api.manhwaweb.com"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/manhwa/nuevos", Fetch.XHR).parseAs<PayloadPopularDto>()
        val mangas = (result.data.weekly + result.data.total)
            .distinctBy { it.slug }
            .sortedByDescending { it.views }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$apiUrl/latest/new-manhwa", Fetch.XHR).parseAs<PayloadLatestDto>()
        val mangas = (result.data.esp + result.data.raw18 + result.data.esp18)
            .distinctBy { it.slug }
            .sortedByDescending { it.latestChapterDate }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.lastOrNull { it.isNotEmpty() } ?: return null
        return getDetailsBySlug(slug).toSManga()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/manhwa/library".toHttpUrl().newBuilder()
            .addQueryParameter("buscar", query)

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> url.addQueryParameter("tipo", filter.toUriPart())

                is DemographyFilter -> url.addQueryParameter("demografia", filter.toUriPart())

                is StatusFilter -> url.addQueryParameter("estado", filter.toUriPart())

                is EroticFilter -> url.addQueryParameter("erotico", filter.toUriPart())

                is GenreFilter -> {
                    val genres = filter.state
                        .filter { it.state }
                        .joinToString("a") { it.id.toString() }
                    url.addQueryParameter("generes", genres)
                }

                is SortByFilter -> {
                    url.addQueryParameter(
                        "order_dir",
                        if (filter.state!!.ascending) "asc" else "desc",
                    )
                    url.addQueryParameter("order_item", filter.selected)
                }

                else -> {}
            }
        }

        url.addQueryParameter("page", (page - 1).toString())

        val response = client.get(url.build(), Fetch.XHR)
        val result = response.parseAs<PayloadSearchDto>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, result.hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        val dto = getDetailsBySlug(slug)

        return SMangaUpdate(dto.toSManga(), parseChapterList(dto))
    }

    private suspend fun getDetailsBySlug(slug: String) = client.get("$apiUrl/manhwa/see/$slug", Fetch.XHR).parseAs<ComicDetailsDto>()

    private fun parseChapterList(comic: ComicDetailsDto) = comic.chapters
        .flatMap { chapter -> chapter.versions.mapNotNull { it.toSChapter(chapter.number, comic.id, comic.slug) } }
        .sortedByDescending { it.chapter_number }

    private fun ChapterVersionDto.toSChapter(number: Float, id: String, realId: String): SChapter? {
        // Unreleased chapters have no date
        val date = create ?: return null

        return SChapter.create().apply {
            name = "Capítulo ${number.toString().removeSuffix(".0")}"
            chapter_number = number
            date_upload = date
            setUrlWithoutDomain(link.replace(id, realId))
            scanlator = if (joint.isEmpty()) {
                "ManhwaWeb Bot"
            } else {
                joint.map { it.name.trim() }.filter { it.isNotEmpty() }.joinToString().ifEmpty { null }
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.removeSuffix("/").substringAfterLast("/")

        val response = client.get("$apiUrl/chapters/see/$slug", Fetch.XHR)

        val result = response.parseAs<PayloadPageDto>()
        return result.data.images.filter { it.startsWith("http") }
            .mapIndexed { i, img -> Page(i, imageUrl = img) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        TypeFilter(),
        DemographyFilter(),
        StatusFilter(),
        EroticFilter(),
        Filter.Separator(),
        GenreFilter("Géneros", getGenres()),
        Filter.Separator(),
        SortByFilter("Ordenar por", getSortProperties()),
    )
}
