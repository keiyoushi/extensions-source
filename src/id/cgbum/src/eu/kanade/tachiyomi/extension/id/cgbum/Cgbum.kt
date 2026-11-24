package eu.kanade.tachiyomi.extension.id.cgbum

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.SecureRandom

@Source
abstract class Cgbum : KeiSource() {

    private val sid = ByteArray(24).also(SecureRandom()::nextBytes).toHexString()

    @Volatile
    private var grant = ""

    @Volatile
    private var nextPage: NextPage? = null

    private val readerAccessHeaders
        get() = headers.newBuilder()
            .set("X-Reader-Access", "1")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/populer".toHttpUrl(), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl().addQueryParameter("sort", "latest").build(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/cari".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("sort", filters.firstInstance<SearchSortFilter>().value)
                .build()
            return getMangaList(url, page)
        }

        val url = listUrl().apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()
        return getMangaList(url, page)
    }

    private fun listUrl(): HttpUrl.Builder = "$baseUrl/daftar-komik".toHttpUrl().newBuilder()

    private suspend fun getMangaList(listing: HttpUrl, page: Int): MangasPage {
        val url = nextPage?.takeIf { it.listing == listing && it.page == page }?.url
            ?: listing.newBuilder().setQueryParameter("page", page.toString()).build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.comic-card").map(::mangaFromCard)
        val next = document.selectFirst("a[rel=next]")?.absUrl("href")?.toHttpUrl()
        nextPage = next?.let { NextPage(listing, page + 1, it) }
        return MangasPage(mangas, next != null)
    }

    private class NextPage(val listing: HttpUrl, val page: Int, val url: HttpUrl)

    private fun mangaFromCard(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h3.comic-card-title a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        status = parseStatus(element.selectFirst(".badge-status")?.text())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in MANGA_PATHS) return null

        val manga = SManga.create().apply { this.url = "/komik/${segments[1]}" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
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
            document.selectFirst(".meta-row:has(.meta-label:containsOwn(Tipe)) .meta-value")?.text()?.let(::add)
            document.select(".comic-genres a.genre-pill").mapTo(this) { it.text() }
        }.joinToString()
        status = parseStatus(document.selectFirst(".comic-cover .badge-status")?.text())
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".synopsis-content"))
            .altTitles(document.selectFirst("p.comic-alt-title")?.text()?.split(",").orEmpty(), "Judul alternatif")
            .build()
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-grid a.ch-grid-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.text()
            chapter_number = element.attr("data-chapter-sort").toFloatOrNull() ?: -1f
        }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select("section[aria-label=\"Komik terkait\"] article.comic-card").map(::mangaFromCard)
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
            add(Filter.Header("Saat mencari judul, hanya Urutkan pencarian yang dipakai"))
            add(SearchSortFilter())
            add(Filter.Separator())
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
        },
    )

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "tamat" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val MANGA_PATHS = setOf("komik", "baca")
        private val READER_ACCESS_REGEX = Regex("""__READER_ACCESS\s*=\s*(\{.*?\});""")
    }
}
