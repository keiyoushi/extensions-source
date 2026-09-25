package eu.kanade.tachiyomi.extension.es.colorcitoscan

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.time.Instant

@Source
abstract class ColorcitoScan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val projects = getComicsList().sortedByDescending { it.trending?.visitas ?: 0L }
        return MangasPage(projects.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val projects = getComicsList().sortedByDescending { Instant.tryParse(it.actualizacionCap) }
        return MangasPage(projects.map { it.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var projects = getComicsList()

        if (query.isNotBlank()) {
            projects = projects.filter { it.containsQuery(query.trim()) }
        }

        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreListFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        statusFilter?.selected?.let { statusId ->
            projects = projects.filter { it.stateId == statusId }
        }

        typeFilter?.selected?.let { typeName ->
            projects = projects.filter { project ->
                project.origins.any { it.origin?.name.equals(typeName, ignoreCase = true) }
            }
        }

        genreFilter?.state?.let { genres ->
            val included = genres.filter { it.isIncluded() }.map { it.name.lowercase(Locale.ROOT) }
            val excluded = genres.filter { it.isExcluded() }.map { it.name.lowercase(Locale.ROOT) }

            if (included.isNotEmpty()) {
                projects = projects.filter { project ->
                    val projectGenres = project.genders.mapNotNull { it.gender?.name?.lowercase(Locale.ROOT) }
                    included.all { it in projectGenres }
                }
            }

            if (excluded.isNotEmpty()) {
                projects = projects.filter { project ->
                    val projectGenres = project.genders.mapNotNull { it.gender?.name?.lowercase(Locale.ROOT) }
                    excluded.none { it in projectGenres }
                }
            }
        }

        sortFilter?.let { sort ->
            val ascending = sort.state?.ascending ?: false
            projects = when (sort.state?.index) {
                0 -> if (ascending) {
                    projects.sortedBy { it.trending?.visitas ?: 0L }
                } else {
                    projects.sortedByDescending { it.trending?.visitas ?: 0L }
                }
                1 -> if (ascending) {
                    projects.sortedBy { Instant.tryParse(it.actualizacionCap) }
                } else {
                    projects.sortedByDescending { Instant.tryParse(it.actualizacionCap) }
                }
                2 -> if (ascending) {
                    projects.sortedBy { it.name.lowercase(Locale.ROOT) }
                } else {
                    projects.sortedByDescending { it.name.lowercase(Locale.ROOT) }
                }
                3 -> if (ascending) {
                    projects.sortedBy { it.averageRating ?: 0.0 }
                } else {
                    projects.sortedByDescending { it.averageRating ?: 0.0 }
                }
                else -> projects
            }
        }

        return MangasPage(projects.map { it.toSManga() }, false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        Filter.Separator(),
        GenreListFilter(getGenreList()),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrlOrNull()?.host) return null
        val slug = url.encodedPath.trimEnd('/').substringAfterLast('/')
        val response = client.get("$baseUrl/api/showProject/$slug").parseAs<ProjectDetailsResponseDto>()
        return response.response.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trimEnd('/').substringAfterLast('/')
        val response = client.get("$baseUrl/api/showProject/$slug").parseAs<ProjectDetailsResponseDto>()
        val project = response.response
        return SMangaUpdate(project.toSManga(), project.toChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val images = document.select("img[src*=/serie/], img[src*=%2Fserie%2F]")
            .map { extractDirectImageUrl(it.attr("src")) }
            .distinct()

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    private suspend fun getComicsList(): List<ComicDataDto> = client.get("$baseUrl/api/comics").parseAs<ComicsResponseDto>().response

    private fun extractDirectImageUrl(url: String): String = url.toHttpUrlOrNull()?.queryParameter("url") ?: url
}
