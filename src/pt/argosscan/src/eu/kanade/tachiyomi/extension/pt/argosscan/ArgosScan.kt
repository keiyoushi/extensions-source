package eu.kanade.tachiyomi.extension.pt.argosscan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class ArgosScan : KeiSource() {

    private val apiUrl = "https://api.argoscomics.online"

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()

        if (request.url.host.startsWith("api.")) {
            val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            // Fixed the cookie name check from "argos_auth_token" to "session"
            val hasAuth = cookies.any { it.name == "session" && it.value.isNotEmpty() }

            if (!hasAuth) {
                throw IOException("Login necessário. Abra o WebView e faça login com o Discord para usar a extensão.")
            }
        }

        val response = chain.proceed(request)

        if (response.code == 401 || response.code == 403) {
            throw IOException("Sessão expirada. Faça login novamente no WebView.")
        }

        response
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = (
        client.get("$apiUrl/projects").parseAs<Projects>().toMangasPage()
        )

    // =============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return fetchDetails(slug).toSManga()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = (
        client.get("$apiUrl/projects").parseAs<Projects>().toMangasPage(query)
        )

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")
        val projectId = manga.memo["projectId"]?.string

        if (projectId == null) {
            val project = fetchDetails(slug)
            return SMangaUpdate(
                project.toSManga(),
                if (fetchChapters) fetchChapters(project.id) else chapters,
            )
        }
        return coroutineScope {
            val updatedManga = async {
                if (fetchDetails) fetchDetails(slug).toSManga() else manga
            }
            val updatedChapters = async {
                if (fetchChapters) fetchChapters(projectId) else chapters
            }
            SMangaUpdate(updatedManga.await(), updatedChapters.await())
        }
    }

    private suspend fun fetchDetails(slug: String) = client.get("$apiUrl/projects/slug/$slug").parseAs<Project>()

    private suspend fun fetchChapters(projectId: String) = (
        client.get("$apiUrl/chapters?kind=published&project_id=$projectId")
            .parseAs<Chapters>()
            .toSChapterList(projectId)
        )

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = chapter.memo["images"]?.parseAs<List<String>>()
            ?: error("Atualizar mangá")

        return images.ifEmpty {
            error("Capítulo não encontrado.")
        }.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()
}
