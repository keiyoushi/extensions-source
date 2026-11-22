package eu.kanade.tachiyomi.extension.id.cgbum

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.security.SecureRandom

@Source
abstract class Cgbum : KeiSource() {

    private val sid = ByteArray(24).also(SecureRandom()::nextBytes).toHexString()

    @Volatile
    private var grant = ""

    private val readerAccessHeaders
        get() = headers.newBuilder()
            .set("X-Reader-Access", "1")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl(page).addQueryParameter("sort", "views").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl(page).addQueryParameter("sort", "latest").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/cari".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return getMangaList(url)
        }

        val url = listUrl(page).apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()
        return getMangaList(url)
    }

    private fun listUrl(page: Int): HttpUrl.Builder = "$baseUrl/daftar-komik".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.comic-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3.comic-card-title a")!!
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in MANGA_PATHS) return null

        val manga = SManga.create().apply { this.url = "/komik/${segments[1]}" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document).apply { url = manga.url },
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("button[data-title]")!!.attr("data-title")
        thumbnail_url = document.selectFirst(".comic-cover img")?.absUrl("src")
        author = document.selectFirst(".meta-row:has(.meta-label:containsOwn(Author)) .meta-value")
            ?.text()
            ?.takeIf { it != "-" }
        genre = buildList {
            document.select(".comic-genres a.genre-pill").mapTo(this) { it.text() }
            document.selectFirst(".meta-row:has(.meta-label:containsOwn(Tipe)) .meta-value")?.text()?.let(::add)
        }.joinToString()
        status = when (document.selectFirst(".comic-cover .badge-status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "tamat" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            document.selectFirst(".synopsis-content")?.textOrNull()?.let(::append)
            document.selectFirst("p.comic-alt-title")?.textOrNull()?.let {
                if (isNotEmpty()) append("\n\n")
                append("Judul alternatif: ", it)
            }
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-grid a.ch-grid-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.text()
            chapter_number = element.attr("data-chapter").toFloatOrNull() ?: -1f
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val readerAccess = document.selectFirst("script:containsData(__READER_ACCESS =)")!!.data()
            .let { READER_ACCESS_REGEX.find(it)!!.groupValues[1] }
            .parseAs<ReaderAccessDto>()
        val endpoint = baseUrl.toHttpUrl().resolve(readerAccess.endpoint)!!
        val fetch = Fetch.XHR.from(chapterUrl)
        suspend fun access(body: ReaderAccessRequest) = client.post(endpoint, readerAccessHeaders, fetch, body.toJsonRequestBody())
            .parseAs<ReaderAccessResponse>()

        var response = access(ReaderAccessRequest(sid, readerAccess.page, grant))
        response.challenge?.let { challenge ->
            response = access(
                ReaderAccessRequest(
                    sid = sid,
                    page = readerAccess.page,
                    token = "",
                    ticket = challenge.ticket,
                    nonce = solveChallenge(challenge.seed, challenge.difficulty),
                ),
            )
        }
        response.grant?.let { grant = it.token }

        val images = response.images ?: throw Exception("Gagal memuat gambar, coba lagi")
        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    private fun solveChallenge(seed: String, difficulty: Int): String = generateSequence(0L) { it + 1 }
        .first { nonce -> "$seed$nonce".sha256().leadingZeroBits() >= difficulty }
        .toString()

    private fun ByteArray.leadingZeroBits(): Int {
        val index = indexOfFirst { it != 0.toByte() }
        if (index == -1) return size * 8
        return index * 8 + Integer.numberOfLeadingZeros(this[index].toInt() and 0xFF) - 24
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/daftar-komik", Fetch.DOCUMENT).asJsoup()
        return document.select("#filterForm input[name=\"genres[]\"]")
            .map { it.attr("value") }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(Filter.Header("Filter diabaikan saat mencari judul"))
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
        },
    )

    companion object {
        private val MANGA_PATHS = setOf("komik", "baca")
        private val READER_ACCESS_REGEX = Regex("""__READER_ACCESS\s*=\s*(\{.*?\});""")
    }
}
