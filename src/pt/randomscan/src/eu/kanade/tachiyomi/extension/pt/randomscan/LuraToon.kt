package eu.kanade.tachiyomi.extension.pt.randomscan

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class LuraToon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(::pageHeadersInterceptor)
        addInterceptor(ZipInterceptor()::zipImageInterceptor)
        addInterceptor(::loggedVerifyInterceptor)
        rateLimit(3)
    }

    // Popular
    override suspend fun getPopularManga(page: Int) = coroutineScope {
        val top10 = async { client.get("$baseUrl/api/main/").parseAs<MainPage>().top10 }
        val obras = async { client.get("$baseUrl/api/obras/").parseAs<SearchResponse>().obras }
        MangasPage(
            (top10.await() + obras.await()).distinctBy {
                it.slug
            }.map { it.toSManga(baseUrl) },
            false,
        )
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int) = client.get(
        "$baseUrl/api/main/?part=${page - 1}",
    ).parseAs<MainPage>().latest.map { it.toSManga(baseUrl) }.let {
        MangasPage(it, page < 2)
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(0) ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = slug },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = client.get(
        "$baseUrl/api/autocomplete/".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .build(),
    ).parseAs<SearchResponse>().obras.map { it.toSManga(baseUrl) }.let {
        MangasPage(it, false)
    }

    // MangaUpdate
    override val supportRelatedMangasBySearch = true

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["mangaSlug"]?.string
        return "$baseUrl/$slug/${chapter.url}/"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trim('/')
        val res = client.get(
            "$baseUrl/api/obra/$slug/",
            ensureSuccess = false,
        )
        if (res.code == 404) res.throwError(MIGRATE)

        val comic = res.parseAs<MangaDetail>()

        val updatedManga = SManga.create().apply {
            with(comic) {
                url = "/$slug/"
                title = titulo
                author = autor
                artist = artista
                genre = (
                    listOf(tipo) + generos.map { it.name }
                    ).joinToString()
                this@apply.status = when (status) {
                    "Em Lançamento" -> SManga.ONGOING
                    "Finalizado" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                thumbnail_url = "$baseUrl$capa"
                description = sinopse
            }
        }

        val updatedChapters = comic.caps.sortedByDescending {
            it.num
        }.map { it.toSChapter(slug) }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // PageList
    private var userId: Long? = null

    private suspend fun getUserId() = userId ?: run {
        client.get("$baseUrl/api/user-info/").parseAs<User>()
            .takeIf {
                it.authorized
            }?.userid.also { userId = it } ?: error(LOGIN)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaSlug = chapter.memo["mangaSlug"]?.string ?: error(REFRESH)
        val capSlug = chapter.url
        val userId = getUserId()
        val res = client.get(
            baseUrl + "/api/$OBRA_PATH/$mangaSlug/$capSlug/",
            cacheControl = FORCE_NETWORK,
        )

        val chapterInfo = res.decrypt(
            capSlug + mangaSlug + userId,
            8,
        ).parseAs<CapituloPagina>()

        val mangaId = chapterInfo.obra.id

        val key = "$mangaId$capSlug$userId"
        return (0 until chapterInfo.files).map { i ->
            val token = if (i == 0) res.header("token").orEmpty() else ""
            Page(
                i,
                "$baseUrl/download/$mangaId/$capSlug/$i/?api=2" +
                    "#$token|$key",
            )
        }
    }

    // Stateful cookies
    private val imageMutex = Mutex()
    override suspend fun getImageUrl(page: Page) = imageMutex.withLock {
        page.imageUrl ?: page.url
    }

    // Interceptors
    private fun pageHeadersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if ("cap-download" in url.pathSegments) throw IOException(REFRESH)

        if ("download" in url.pathSegments) {
            val token = url.fragment!!.substringBefore("|")

            if (!token.isNullOrEmpty()) {
                val cookie = request.header("Cookie")
                    ?.split("; ")
                    ?.filterNot { it.startsWith("csac=") }
                    ?.joinToString("; ")
                return chain.proceed(
                    request.newBuilder()
                        .header("Token", token)
                        .header("Cookie", cookie.orEmpty())
                        .build(),
                )
            }
        }
        return chain.proceed(request)
    }

    private fun loggedVerifyInterceptor(chain: Interceptor.Chain): Response {
        with(chain.proceed(chain.request())) {
            val segments = request.url.pathSegments
            if (segments.contains("login") || segments.isEmpty()) {
                throwError(LOGIN)
            }
            if (code == 429) throwError(RATELIMITED)
            if (code == 402) throwError(VIP)
            return this
        }
    }

    private fun Response.throwError(message: String): Nothing = use {
        throw IOException(message)
    }

    companion object {
        const val LOGIN = "Faça o login na WebView para acessar o contéudo"
        const val MIGRATE = "Capítulos não encontrados. Tente migrar o mangá"
        const val RATELIMITED =
            "A LuraToon bloqueou seu acesso. Aguarde 1 minuto e tente novamente."
        const val VIP = "Assine o VIP para acessar"
        const val REFRESH = "Atualizar mangá"
        const val OBRA_PATH = "484d2a13"
    }
}
