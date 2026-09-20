package eu.kanade.tachiyomi.extension.pt.argoscomics

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArgosComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = readTimeout(2.minutes)
        .callTimeout(3.minutes)
        .rateLimit(3, 2.seconds)

    private val rscHeaders
        get() = headersBuilder().set("rsc", "1").build()

    private val preferences by getPreferencesLazy()

    private val tokenManager by lazy { TokenManager(preferences) }

    // ======================== Popular =============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("projetos")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, rscHeaders).extractNextJs<MangasListDto>()!!.toMangasPage()
    }

    // ======================== Latest =============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get(baseUrl, rscHeaders).extractNextJs<LatestMangas>()!!.toMangasPage()

    // ======================== Search =============================

    private suspend fun getSearchToken(url: String): String? = tokenManager.search?.value
        ?: findToken(url, SEARCH_TOKEN_REGEX, tokenManager.search) { value, url ->
            tokenManager.search = Token(value, url)
        }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchHeaders = headers.newBuilder()
            .set("Next-Action", getSearchToken(baseUrl) ?: throw Exception(WARNING))
            .build()
        val payload = listOf(query).toJsonRequestBody()
        val dto = client.post(baseUrl, searchHeaders, payload).extractNextJs<List<MangaDto>>() ?: emptyList()
        return MangasPage(dto.map(MangaDto::toSManga), false)
    }

    // ======================== Details + Chapters =============================

    private suspend fun getChapterToken(scriptUrl: String): String? = tokenManager.chapter?.value
        ?: findToken(scriptUrl, CHAPTER_TOKEN_REGEX, tokenManager.chapter) { value, url ->
            tokenManager.chapter = Token(value, url)
        }

    private suspend fun getDetailsToken(url: String): String? = tokenManager.details?.value
        ?: findToken(url, DETAILS_TOKEN_REGEX, tokenManager.details) { value, url ->
            tokenManager.details = Token(value, url)
        }

    private suspend fun findToken(url: String, regex: Regex, latest: Token?, build: (String, String) -> Unit): String? {
        if (latest != null) {
            regex.find(client.get(url).body.string())?.groupValues?.last()
                ?.also {
                    build(it, latest.url.toString())
                    return it
                }
        }

        val urls = getNextJSChunks(url)

        for (url in urls) {
            val token = regex.find(client.get(url).body.string())
                ?.groupValues?.last()
                ?.also { build(it, url.toString()) }
            if (!token.isNullOrBlank()) return token
        }
        return null
    }

    private suspend fun getNextJSChunks(url: String): List<HttpUrl> {
        val document = client.get(url, ensureSuccess = false).asJsoup()
        val chunksElement = document.select("script[src*=chunks]:not([nomodule]):not([id])")
            .map { it.absUrl("src") }
            .reversed()

        val chunksLazyLoad = document.select("script:containsData(chunks)").joinToString("\n") { it.data() }.let {
            NEXT_CHUNKS_REGEX.findAll(it).flatMap(MatchResult::groupValues).toSet()
        }.map { "$baseUrl$it" }

        val urls = (chunksElement + chunksLazyLoad)
            .mapNotNull { it.toHttpUrlOrNull() }
            .distinct()
        return urls
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) {
                val url = getMangaUrl(manga)
                val payload = url.toHttpUrl().pathSegments.toJsonRequestBody()
                val detailsHeaders = headers.newBuilder()
                    .set("Next-Action", getDetailsToken(url) ?: throw Exception(WARNING))
                    .build()
                client.post(url, detailsHeaders, payload).extractNextJs<MangaDetailsDto>()!!.toSManga()
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val url = getMangaUrl(manga)
                val payload = url.toHttpUrl().pathSegments.toJsonRequestBody()
                val chaptersHeaders = headers.newBuilder()
                    .set("Next-Action", getChapterToken(url) ?: throw Exception(WARNING))
                    .build()
                val response = client.post(url, chaptersHeaders, payload)
                val pathSegment = url.substringAfter(baseUrl)
                response.extractNextJs<VolumeChapterDto>()!!.toChapterList(pathSegment)
            } else {
                chapters
            }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    // ======================== Pages =============================

    private suspend fun getPagesToken(url: String): String? = tokenManager.page?.value
        ?: findToken(url, PAGES_TOKEN_REGEX, tokenManager.page) { value, url ->
            tokenManager.page = Token(value, url)
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter)
        val segments = url.toHttpUrl().pathSegments
        val payload = listOf(segments.first(), segments.last()).toJsonRequestBody()
        val pagesHeaders = headers.newBuilder()
            .set("Next-Action", getPagesToken(url) ?: throw Exception(WARNING))
            .build()
        val response = client.post(url, pagesHeaders, payload)
        if (response.request.url.encodedPath == "/login") error("Acesse sua conta")
        val body = response.body.string()
        val dto = body.extractNextJsRsc<MangaDetailsDto>()
        if (dto?.isUpcoming == true) error("Capítulo em desenvolvimento")
        return body.extractNextJsRsc<PagesDto>()!!.toPageList()
    }

    @Serializable
    class Token(
        val value: String,
        val url: String? = null,
        val updatedAt: Long = System.currentTimeMillis(),
    ) {
        fun isExpired() = updatedAt + TTL < System.currentTimeMillis()

        companion object {
            val TTL = 1.days.inWholeMilliseconds
        }
    }

    private class TokenManager(
        private val preferences: SharedPreferences,
    ) {

        private fun get(key: String): Token? = preferences.getString(key, null)
            ?.parseAs<Token>()
            ?.takeUnless(Token::isExpired)

        private fun set(key: String, value: Token) {
            preferences.edit()
                .putString(key, value.toJsonString())
                .apply()
        }

        var search: Token?
            get() = get(SEARCH_TOKEN_PREF)
            set(value) {
                set(SEARCH_TOKEN_PREF, value!!)
            }

        var details: Token?
            get() = get(DETAILS_TOKEN_PREF)
            set(value) {
                set(DETAILS_TOKEN_PREF, value!!)
            }

        var chapter: Token?
            get() = get(CHAPTER_TOKEN_PREF)
            set(value) {
                set(CHAPTER_TOKEN_PREF, value!!)
            }

        var page: Token?
            get() = get(PAGES_TOKEN_PREF)
            set(value) {
                set(PAGES_TOKEN_PREF, value!!)
            }

        companion object {
            private const val SEARCH_TOKEN_PREF = "searchTokenPref"
            private const val CHAPTER_TOKEN_PREF = "chapterTokenPref"
            private const val DETAILS_TOKEN_PREF = "detailsTokenPref"
            private const val PAGES_TOKEN_PREF = "pagesTokenPref"
        }
    }

    companion object {
        private val SEARCH_TOKEN_REGEX = buildTokenRegex("search")
        private val CHAPTER_TOKEN_REGEX = buildTokenRegex("getAllChapters")
        private val DETAILS_TOKEN_REGEX = buildTokenRegex("getOne")
        private val PAGES_TOKEN_REGEX = buildTokenRegex("getPages")

        private val NEXT_CHUNKS_REGEX = """/_next/static/chunks/\w+.js""".toRegex()

        private const val WARNING = "Não foi possivel obter os dados"

        private fun buildTokenRegex(ref: String) = """=.+createServerReference\)\("([^"]+)"[\s\S]*?"$ref"""".toRegex(RegexOption.IGNORE_CASE)
    }
}
