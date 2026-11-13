package eu.kanade.tachiyomi.multisrc.catharsis

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.cronet.cronet
import keiyoushi.lib.cronet.isCronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

abstract class Catharsis : KeiSource() {

    protected abstract val apiUrl: String

    // What the site's own API calls send
    protected abstract val apiHeaders: Headers

    // First path segment of a series page
    protected abstract val mangaPath: String

    protected abstract fun mangaKey(manga: MangaDto): String

    // The source's reader interceptors, ahead of the theme's pacing and Cronet
    protected abstract fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder

    // The backend rejects non-browser TLS with CLIENT_NOT_ALLOWED
    final override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addReaderInterceptors()
        .rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host && it.pathSegments.first() != "assets" && !it.encodedPath.startsWith("/lector/p/") }
        .cronet()

    override suspend fun getPopularManga(page: Int) = searchMangas(page, "", "-n_visitas", null, null, null, emptyList())

    override suspend fun getLatestUpdates(page: Int) = searchMangas(page, "", "-fecha_ultimo_capitulo", null, null, null, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        return searchMangas(
            page = page,
            query = query,
            sort = filters.firstInstance<SortFilter>().value,
            status = filters.firstInstance<StatusFilter>().value,
            letter = filters.firstInstance<LetterFilter>().value,
            scan = filters.firstInstanceOrNull<ScanFilter>()?.value,
            genres = genres,
        )
    }

    private suspend fun searchMangas(page: Int, query: String, sort: String, status: String?, letter: String?, scan: String?, genres: List<String>): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            if (query.isNotBlank()) addQueryParameter("name", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
            status?.let { addQueryParameter("status", it) }
            letter?.let { addQueryParameter("letter", it) }
            scan?.let { addQueryParameter("scan", it) }
            addQueryParameter("sort", sort)
        }.build()
        val result = client.get(url, apiHeaders, Fetch.XHR).parseAs<MangaListDto>()
        return MangasPage(result.data.map { it.toSManga(mangaKey(it), baseUrl) }, page < result.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != mangaPath) return null
        val key = url.pathSegments.getOrNull(1) ?: return null
        val dto = client.get("$apiUrl/mangas/$key", apiHeaders, Fetch.XHR).parseAs<MangaDto>()
        return dto.toSManga(mangaKey(dto), baseUrl).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$mangaPath/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$apiUrl/mangas/${manga.url}", apiHeaders, Fetch.XHR).parseAs<JsonElement>()
        val dto = details.parseAs<MangaDto>()
        return SMangaUpdate(
            dto.toSManga(mangaKey(dto), baseUrl),
            details.parseAs<MangaChaptersDto>().capitulos
                .filter { it.estado == null || it.estado == "publicado" }
                .sortedWith(compareByDescending<ChapterDto> { it.temporada }.thenByDescending { it.numero })
                .map { it.toSChapter(manga.url, dto.scanlator, chapterDate(it)) },
        )
    }

    protected open fun chapterDate(chapter: ChapterDto) = Instant.tryParse(chapter.fechaPublicacion ?: chapter.dateCreated)

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async { client.get("$apiUrl/mangas/genres", apiHeaders, Fetch.XHR).parseAs<List<GenreDto>>() }
        val scans = async { client.get("$apiUrl/mangas/scans", apiHeaders, Fetch.XHR).parseAs<List<ScanFilterDto>>() }
        FilterDataDto(genres.await(), scans.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterDataDto>()
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(LetterFilter())
                filterData?.scans?.takeIf { it.isNotEmpty() }?.let { scans ->
                    add(ScanFilter(scans.map { it.nombre to it.id.toString() }))
                }
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    add(GenreFilter(genres.map { GenreCheckBox(it.nombre, it.id) }))
                }
            },
        )
    }

    private val readerPassMutex = Mutex()
    private var currentReaderPass: ReaderPassDto? = null

    // Reader pass from the site's Cap proof-of-work
    protected suspend fun readerPass(fetch: Fetch, renew: Boolean = false): ReaderPassDto = readerPassMutex.withLock {
        if (!renew) {
            (currentReaderPass ?: storedReaderPass())
                ?.takeIf { Instant.fromEpochMilliseconds(it.expiresAt) > Clock.System.now() + 1.minutes }
                ?.let { return@withLock it.also { currentReaderPass = it } }
        }
        val system = client.get("$apiUrl/system", apiHeaders, fetch).parseAs<SystemDto>()
        val capUrl = system.capApiEndpoint.toHttpUrl()
        val challenge = client.post(capUrl.resolve("challenge")!!, Fetch.XHR, RequestBody.EMPTY).parseAs<CapChallengeResponse>()
        val solutions = (1..challenge.challenge.count).map { i ->
            solveCap(
                salt = capPrng("${challenge.token}$i", challenge.challenge.saltSize),
                target = capPrng("${challenge.token}${i}d", challenge.challenge.difficulty),
            )
        }
        val redeemed = client.post(
            capUrl.resolve("redeem")!!,
            Fetch.XHR,
            CapRedeemRequest(challenge.token, solutions).toJsonRequestBody(),
        ).parseAs<CapRedeemResponse>()
        val capToken = redeemed.token?.takeIf { redeemed.success }
            ?: throw Exception("Reader verification failed")
        redeemReaderPass(capToken, system.capSitekey, fetch).also { currentReaderPass = it }
    }

    // A pass the site gave in an earlier session, used before solving a new one
    protected open fun storedReaderPass(): ReaderPassDto? = null

    // Trades the solved Cap token for the site's reader pass
    protected open suspend fun redeemReaderPass(capToken: String, sitekey: String, fetch: Fetch): ReaderPassDto = client.post(
        "$apiUrl/mangas/reader-pass",
        apiHeaders,
        fetch,
        ReaderPassRequest(capToken, sitekey).toJsonRequestBody(),
        ensureSuccess = false,
    ).ensureSiteSuccess().parseAs()

    // FNV-1a seeded xorshift32, as hex
    private fun capPrng(seed: String, length: Int): String {
        var state = seed.fold(FNV_OFFSET_BASIS) { hash, char -> (hash xor char.code) * FNV_PRIME }
        return buildString {
            while (this.length < length) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                append(state.toUInt().toString(16).padStart(8, '0'))
            }
        }.take(length)
    }

    private fun solveCap(salt: String, target: String): Int = generateSequence(0) { it + 1 }
        .first { "$salt$it".sha256().toHexString().startsWith(target) }

    protected fun Response.apiError(): ApiErrorDto? {
        if (header("Content-Type")?.startsWith("application/json") != true) {
            close()
            return null
        }
        return parseAs<ApiErrorDto>()
    }

    protected fun Response.ensureSiteSuccess(): Response {
        if (!isSuccessful) throw siteError(apiError()?.code)
        return this
    }

    // Site error codes with a message for the user
    protected fun Response.siteError(apiCode: String?, status: Int = code): Exception = when (apiCode) {
        "CLIENT_NOT_ALLOWED" -> if (isCronet) {
            Exception("The site refused the app's connection")
        } else {
            Exception("Unsupported device")
        }
        "DATACENTER_BLOCKED" -> Exception("Turn off your VPN or proxy to read on this site")
        "EARLY_ACCESS" -> Exception("This chapter is still in early access on the site")
        else -> HttpException(status)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val FNV_OFFSET_BASIS = 0x811c9dc5.toInt()
        private const val FNV_PRIME = 0x01000193
    }
}
