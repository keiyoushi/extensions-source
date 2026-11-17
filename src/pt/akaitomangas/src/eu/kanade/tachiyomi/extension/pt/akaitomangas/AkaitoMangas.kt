package eu.kanade.tachiyomi.extension.pt.akaitomangas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

@Source
abstract class AkaitoMangas : KeiSource() {

    override val supportsFilterFetching = true

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val dto = client.get("$baseUrl/rank", Fetch.DOCUMENT).asJsoup().extractNextJs<RankDto>()!!
        return MangasPage(dto.initialProjects.map(ProjectDto::toSManga), false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("section:has(h2:containsOwn(Últimas Atualizações)) a:has(h3)").map(::mangaFromCard)
        return MangasPage(mangas, false)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val mangas = "$baseUrl/".postAction(listOf(query.trim()).toJsonRequestBody())
                .extractNextJs<List<ProjectDto>>()
                .orEmpty()
                .map(ProjectDto::toSManga)
            return MangasPage(mangas, false)
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(filters.firstInstance<SectionFilter>().path)
            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.selected.ifEmpty { null }?.let { addQueryParameter("status", it) }
                    is GenreFilter -> filter.selected.ifEmpty { null }?.let { addQueryParameter("genders", it) }
                    is AuthorFilter -> filter.state.trim().ifEmpty { null }?.let { addQueryParameter("author", it) }
                    is ArtistFilter -> filter.state.trim().ifEmpty { null }?.let { addQueryParameter("artist", it) }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li > a:has(h3)").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst("a[href$=page=${page + 1}]") != null)
    }

    private fun mangaFromCard(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(0)?.takeIf(UUID_REGEX::matches) ?: return null
        val link = url.pathSegments.getOrNull(1)?.ifEmpty { null } ?: return null
        val mangaUrl = "/$id/$link"
        return getMangaPage(mangaUrl).project.toSManga().apply { this.url = mangaUrl }
    }

    // =========================== Manga Update ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = getMangaPage(manga.url)
        val updatedChapters = dto.groups
            .flatMap(GroupDto::chapters)
            .sortedByDescending(ChapterDto::number)
            .map { it.toSChapter(manga.url) }
        return SMangaUpdate(dto.project.toSManga(), updatedChapters)
    }

    private suspend fun getMangaPage(mangaUrl: String): MangaPageDto = client.get("$baseUrl$mangaUrl", Fetch.DOCUMENT).asJsoup().extractNextJs<MangaPageDto>()!!

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.extractNextJs<PagesDto>()?.pages.orEmpty()
            .sortedBy(PageDto::pageNumber)
            .mapIndexed { index, page -> Page(index, imageUrl = page.photo) }
    }

    // ============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/projetos", Fetch.DOCUMENT).asJsoup()
        .extractNextJs<GenresDto>()!!
        .availableGenders
        .map(NameDto::name)
        .distinct()
        .sorted()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<String>>())

    // ============================= Utilities =============================

    @Volatile
    private var searchActionId = "40189cc42487e2654ac4d0cdb335ffc43d47dd6b70"

    private val searchActionIdLock = Mutex()

    private suspend fun String.postAction(body: RequestBody): Response {
        val url = this

        suspend fun send(actionId: String, ensureSuccess: Boolean) = client.post(
            url,
            headersBuilder().add("next-action", actionId).build(),
            Fetch.XHR,
            body,
            ensureSuccess = ensureSuccess,
        )

        val staleId = searchActionId
        val response = send(staleId, ensureSuccess = false)
        if (response.isSuccessful) return response

        val code = response.code
        response.close()
        if (code != 404) throw IOException("HTTP error $code")

        refreshSearchActionId(staleId)
        return send(searchActionId, ensureSuccess = true)
    }

    private suspend fun refreshSearchActionId(staleId: String) {
        searchActionIdLock.withLock {
            if (searchActionId != staleId) return
            scrapeSearchActionId(client.get(baseUrl, Fetch.DOCUMENT, ensureSuccess = false).asJsoup())
                ?.let { searchActionId = it }
        }
    }

    private suspend fun scrapeSearchActionId(document: Document): String? = coroutineScope {
        document.select("script[src]")
            .map { it.absUrl("src") }
            .filter { "/_next/static/" in it && ".js" in it }
            .distinct()
            .map { url ->
                async {
                    client.get(url, Fetch.SCRIPT, ensureSuccess = false).use {
                        ACTION_ID_REGEX.findAll(it.body.string())
                            .map { m -> m.destructured }
                            .firstOrNull { (_, name) -> name == "searchProjects" }
                            ?.component1()
                    }
                }
            }
            .awaitAll()
            .firstNotNullOfOrNull { it }
    }

    private companion object {
        val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        val ACTION_ID_REGEX = Regex(
            """createServerReference\)?\s*\(\s*"([0-9a-fA-F]+)"[^)]*"([^"]+)"\s*\)""",
        )
    }
}
