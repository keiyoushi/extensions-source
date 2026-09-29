package eu.kanade.tachiyomi.extension.es.catharsisworld

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CatharsisWorld : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }
        .addInterceptor(::pageInterceptor)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("x-api-key", API_KEY)
        .add("System", "catharsis")
        .add("X-FK-Sistema", "3")

    override suspend fun getPopularManga(page: Int) = searchMangas(page, "", "-n_visitas", null, emptyList())

    override suspend fun getLatestUpdates(page: Int) = searchMangas(page, "", "-fecha_ultimo_capitulo", null, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        return searchMangas(
            page = page,
            query = query,
            sort = filters.firstInstance<SortFilter>().value,
            status = filters.firstInstance<StatusFilter>().value,
            genres = genres,
        )
    }

    private suspend fun searchMangas(page: Int, query: String, sort: String, status: String?, genres: List<String>): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            if (query.isNotBlank()) addQueryParameter("name", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
            status?.let { addQueryParameter("status", it) }
            addQueryParameter("sort", sort)
        }.build()
        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, page < result.totalPages)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/mangas/genres").parseAs<List<GenreDto>>().toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            data?.parseAs<List<GenreDto>>()?.let { genres ->
                add(GenreFilter(genres.map { GenreCheckBox(it.nombre, it.id) }))
            }
        },
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "manga") return null
        return client.get("$apiUrl/mangas/${segments[1]}").parseAs<MangaDto>().toSMangaDetails(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, number) = chapter.url.split("/", limit = 2)
        return "$baseUrl/manga/$mangaId/chapter/$number"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/mangas/${manga.url}").parseAs<MangaDto>()
        return SMangaUpdate(
            dto.toSMangaDetails(baseUrl),
            dto.capitulos
                .filter { it.estado == null || it.estado == "publicado" }
                .sortedByDescending { it.number }
                .map { it.toSChapter(manga.url) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/mangas/${chapter.url}")
        .parseAs<PagesDto>()
        .paginas
        .mapIndexed { i, token -> Page(i, imageUrl = "$apiUrl/mangas/pages/$token") }

    // Page images are XOR-ed with a single-byte key unless they already start with an image signature
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!chain.request().url.encodedPath.startsWith("/api/mangas/pages/") || !response.isSuccessful) return response

        val body = response.body
        val source = body.source()
        if (!source.request(HEADER_SIZE.toLong())) return response
        val header = source.buffer.snapshot(HEADER_SIZE).toByteArray()
        val decoded = ByteArray(HEADER_SIZE) { (header[it].toInt() xor XOR_KEY).toByte() }
        if (isImage(header) || !isImage(decoded)) return response

        val xorSource = object : ForwardingSource(source) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val chunk = Buffer()
                val read = super.read(chunk, byteCount)
                if (read > 0) {
                    val bytes = chunk.readByteArray()
                    for (i in bytes.indices) bytes[i] = (bytes[i].toInt() xor XOR_KEY).toByte()
                    sink.write(bytes)
                }
                return read
            }
        }
        return response.newBuilder()
            .body(xorSource.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val b = IntArray(HEADER_SIZE) { bytes[it].toInt() and 0xFF }
        return (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46) ||
            (b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) ||
            (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) ||
            (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46) ||
            (b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val API_KEY = "SrfnigkBo3YLbySfIE0DU9WtmlF7Ov4mzakJlBV9ZCw"
        private const val XOR_KEY = 0x43
        private const val HEADER_SIZE = 12
    }
}
