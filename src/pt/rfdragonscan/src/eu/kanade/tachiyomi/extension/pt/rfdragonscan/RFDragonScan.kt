package eu.kanade.tachiyomi.extension.pt.rfdragonscan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class RFDragonScan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::loginInterceptor)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // ============================== Latest ===============================

    override val supportsLatest = false

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/projetos".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        val dto = client.get(
            url.build(),
            headersBuilder()
                .add("Rsc", "1")
                .build(),
        ).extractNextJs<ProjectsPageDto>()
            ?: return MangasPage(emptyList(), false)

        val mangas = dto.projects.map {
            it.toSManga()
        }.filter { it.title.contains(query, true) }

        return MangasPage(mangas, query.isEmpty() && dto.pagination?.hasNextPage == true)
    }

    // ============================ MangaUpdate ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        if (!UUID_REGEX.matches(manga.url)) {
            throw Exception("Migrar mangá de $name para $name (mesma extensão)")
        }

        val mangaUrl = getMangaUrl(manga)
        val segments = mangaUrl.toHttpUrl().pathSegments
        val mangaId = segments[0]
        val mangaSlug = segments[1]

        val body = listOf(mangaId, mangaSlug)
            .toJsonRequestBody()

        val updatedManga = async {
            if (fetchDetails) {
                val response = mangaUrl.postAction("getOne", body)
                val dto = response.extractNextJs<MangaDetailsDto> {
                    it is JsonObject && "synopsis" in it && "title" in it
                } ?: throw IOException("Manga details not found")

                dto.toSManga()
            } else {
                manga
            }
        }

        val updatedChapters = async {
            if (fetchChapters) {
                val response = mangaUrl.postAction("getAllChapters", body)
                val seasonList = response.extractNextJs<SeasonListDto> {
                    it is JsonObject && "groups" in it
                } ?: throw IOException("Chapters not found")

                seasonList.groups.orEmpty()
                    .flatMap { it.chapters.orEmpty() }
                    .filter { it.isUpcoming != true && it.hasRestriction != true }
                    .map { it.toSChapter(mangaId, mangaSlug) }
                    .sortedByDescending { it.name.substringAfter("Capítulo ").toFloatOrNull() ?: 0f }
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val segments = chapterUrl.toHttpUrl().pathSegments
        val mangaId = segments[0]
        val chapterTitle = segments[3]

        val body = listOf(mangaId, chapterTitle)
            .toJsonRequestBody()

        return chapterUrl.postAction("getPages", body)
            .extractNextJs<PagesDto> {
                it is JsonObject && "pages" in it
            }?.toPages() ?: throw IOException("Pages not found")
    }

    // ============================= Utilities =============================

    private val actionIds = ConcurrentHashMap(
        mapOf(
            "getOne" to "605b188667d9bcd4a1fe806764ae5e8861eff89915",
            "getAllChapters" to "60a7496d813253cd0acc35e49c36ec1437f4f9f1eb",
            "getPages" to "701b928d969fea96b2124f4f8453277895cb6fae3b",
        ),
    )

    private val actionIdsLock = Mutex()

    private suspend fun String.postAction(actionName: String, body: RequestBody): Response {
        val url = this

        suspend fun send(actionId: String, ensureSuccess: Boolean) = client.post(
            url,
            headersBuilder()
                .add("next-action", actionId)
                .add("Accept", "text/x-component")
                .add("Referer", url)
                .build(),
            body,
            ensureSuccess = ensureSuccess,
        )

        val staleId = actionIds.getValue(actionName)
        val response = send(staleId, ensureSuccess = false)
        if (response.isSuccessful) return response

        val code = response.code
        response.close()
        if (code != 404) throw IOException("HTTP error $code")

        refreshActionIds(actionName, staleId)
        return send(actionIds.getValue(actionName), ensureSuccess = true)
    }

    private suspend fun refreshActionIds(actionName: String, staleId: String) {
        actionIdsLock.withLock {
            if (actionIds.getValue(actionName) != staleId) return // already refreshed

            actionIds.putAll(scrapeActionIds())
        }
    }

    private suspend fun scrapeActionIds(): Map<String, String> = coroutineScope {
        val scripts = listOf(
            "$baseUrl/00000000/dummy",
            "$baseUrl/00000000/dummy/capitulo/1",
        ).map { url ->
            async {
                client.get(url, ensureSuccess = false)
                    .asJsoup()
                    .select("script[src]")
                    .map { it.absUrl("src") }
            }
        }.awaitAll()
            .flatten()
            .filter { "/_next/static/" in it && ".js" in it }
            .distinct()

        scripts.map { url ->
            async {
                client.get(url, ensureSuccess = false).use {
                    ACTION_ID_REGEX.findAll(it.body.string()).map { m -> m.destructured }.toList()
                }
            }
        }.awaitAll()
            .flatten()
            .associate { (id, name) -> name to id }
            .filterKeys { actionIds.containsKey(it) }
    }

    private fun loginInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        val isLoggedIn = cookies.any { it.name == "access_token" && it.value.isNotEmpty() }

        if (isLoggedIn) {
            val response = chain.proceed(request)
            if (response.code != 401 && response.code != 403) {
                return response
            }
            response.close()
        }

        throw IOException("Faça login pelo WebView.")
    }

    private companion object {
        val UUID_REGEX = Regex("^/[0-9a-fA-F\\-]{36}/.*")

        val ACTION_ID_REGEX = Regex(
            """createServerReference\)?\s*\(\s*"([0-9a-fA-F]+)"[^)]*"([^"]+)"\s*\)""",
        )
    }
}
