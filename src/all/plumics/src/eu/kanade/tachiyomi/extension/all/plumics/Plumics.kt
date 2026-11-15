package eu.kanade.tachiyomi.extension.all.plumics

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
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Plumics : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    // The API allows 240 requests a minute, page images included.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(4)

    override suspend fun getPopularManga(page: Int) = getProjects(page, "popular", SERIES_TYPES)

    override suspend fun getLatestUpdates(page: Int) = getProjects(page, "recent", SERIES_TYPES)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val type = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty()
            val params = listOf(
                "category" to filters.firstInstanceOrNull<CategoryFilter>()?.selected,
                "genre" to filters.firstInstanceOrNull<GenreFilter>()?.selected,
                "status" to filters.firstInstanceOrNull<StatusFilter>()?.selected,
                "format" to filters.firstInstanceOrNull<FormatFilter>()?.selected,
            )
            return getProjects(
                page,
                filters.firstInstanceOrNull<SortFilter>()?.selected ?: "popular",
                if (type.isEmpty()) SERIES_TYPES else listOf(type),
                params,
            )
        }

        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("c", "project")
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<ProjectListDto>()
        // The search covers every language, and novels too.
        val mangas = result.data
            .filter { it.lang == lang && it.category != "novel" }
            .map { it.toSManga(baseUrl) }
        return MangasPage(mangas, result.nextPageUrl != null)
    }

    // The site lists Pro and Starter series apart, so each page holds one page of both.
    private suspend fun getProjects(
        page: Int,
        sort: String,
        types: List<String>,
        params: List<Pair<String, String?>> = emptyList(),
    ): MangasPage {
        val lists = coroutineScope {
            types.map { type ->
                async {
                    val url = "$apiUrl/project/$type".toHttpUrl().newBuilder()
                        .addQueryParameter(sort, "1")
                        .addQueryParameter("lang", lang)
                        .addQueryParameter("page", page.toString())
                        .apply {
                            params.forEach { (name, value) ->
                                if (!value.isNullOrEmpty()) addQueryParameter(name, value)
                            }
                        }
                        .build()
                    client.get(url, Fetch.XHR).parseAs<ProjectListDto>()
                }
            }.awaitAll()
        }
        val projects = lists.flatMap { it.data }.let { projects ->
            if (sort == "recent") projects.sortedByDescending { it.updatedAt } else projects
        }
        return MangasPage(projects.map { it.toSManga(baseUrl) }, lists.any { it.nextPageUrl != null })
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null

        val segments = url.pathSegments
        val projectId = when (segments[0]) {
            "manga", "comics", "webtoon", "bd" -> segments.getOrNull(1)?.substringAfterLast('_')
            "reader" -> when (segments.getOrNull(1)) {
                "p" -> segments.getOrNull(2)
                "c" -> segments.getOrNull(2)?.let { chapterId ->
                    client.get("$apiUrl/reader/$chapterId", Fetch.XHR).parseAs<ReaderDto>().chapter.projectId.toString()
                }
                else -> null
            }
            else -> null
        } ?: return null

        return getProject(projectId).takeIf { it.lang == lang && it.category != "novel" }?.toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The chapter list depends on the project's chapter mode, so the project is always fetched.
        val project = getProject(manga.url.substringAfterLast('_'))
        return SMangaUpdate(
            project.toSManga(baseUrl),
            if (fetchChapters) getChapterList(project) else chapters,
        )
    }

    private suspend fun getProject(id: String) = client.get("$apiUrl/project/$id", Fetch.XHR).parseAs<ProjectResponse>().project

    private suspend fun getChapterList(project: ProjectDetailsDto): List<SChapter> {
        if (!project.chapterMode) return listOf(project.toSingleChapter())

        val firstPage = getChapterPage(project.id, 1)
        val otherPages = coroutineScope {
            (2..firstPage.lastPage).map { async { getChapterPage(project.id, it) } }.awaitAll()
        }
        return (listOf(firstPage) + otherPages).flatMap { it.data }.map { it.toSChapter() }.reversed()
    }

    private suspend fun getChapterPage(projectId: Int, page: Int) = client.get("$apiUrl/project/$projectId/chapters?page=$page", Fetch.XHR).parseAs<ChapterListDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // "/reader/c/<chapter id>", or "/reader/p/<project id>" for a project without chapters
        val (mode, id) = chapter.url.removePrefix("/reader/").split("/")
        val owner = if (mode == "p") "project" else "chapter"
        return client.get("$apiUrl/$owner/$id/pages", Fetch.XHR).parseAs<PageListDto>()
            .pages.mapIndexed { index, page -> Page(index, imageUrl = "$apiUrl/page/${page.id}") }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filters are ignored with a text search"),
        TypeFilter(),
        SortFilter(),
        CategoryFilter(),
        GenreFilter(),
        StatusFilter(),
        FormatFilter(),
    )

    companion object {
        private val SERIES_TYPES = listOf("pro", "starter")
    }
}
