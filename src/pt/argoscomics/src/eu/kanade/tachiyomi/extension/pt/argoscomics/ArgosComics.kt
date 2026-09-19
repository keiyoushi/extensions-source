package eu.kanade.tachiyomi.extension.pt.argoscomics

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
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArgosComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = readTimeout(2.minutes)
        .callTimeout(3.minutes)
        .rateLimit(3, 2.seconds)

    private val rscHeaders
        get() = headersBuilder().set("rsc", "1").build()

    private val customClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .build()
    }

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

    private var searchToken: String? = null
    private suspend fun getSearchToken(url: String): String? = searchToken ?: findToken(url, SEARCH_TOKEN_REGEX) {
        searchToken = it
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
    private var chapterToken: String? = null
    private suspend fun getChapterToken(scriptUrl: String? = null): String? = chapterToken ?: findToken(scriptUrl!!, CHAPTER_TOKEN_REGEX) {
        chapterToken = it
    }

    private var detailsToken: String? = null
    private suspend fun getDetailsToken(url: String): String? = detailsToken ?: findToken(url, DETAILS_TOKEN_REGEX) {
        detailsToken = it
    }

    private suspend fun findToken(url: String, regex: Regex, build: (String) -> Unit): String? {
        val document = customClient.get(url, ensureSuccess = false).asJsoup()
        val chunksElement = document.select("script[src*=chunks]:not([nomodule]):not([id])")
            .map { it.absUrl("src") }
            .reversed()

        val chunksLazyLoad = document.select("script:containsData(chunks)").joinToString("\n") { it.data() }.let {
            NEXT_CHUNKS_REGEX.findAll(it).flatMap(MatchResult::groupValues).toSet()
        }.map { "$baseUrl$it" }

        val urls = (chunksElement + chunksLazyLoad)
            .mapNotNull { it.toHttpUrlOrNull() }
            .distinct()

        for (url in urls) {
            val token = regex.find(customClient.get(url).body.string())?.groupValues?.last()?.also { build(it) }
            if (!token.isNullOrBlank()) return token
        }
        return null
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

    private var pagesToken: String? = null
    private suspend fun getPagesToken(url: String): String? = pagesToken ?: findToken(url, PAGES_TOKEN_REGEX) {
        pagesToken = it
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
