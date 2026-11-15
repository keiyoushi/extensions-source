package eu.kanade.tachiyomi.extension.fr.comicstracker

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@Source
abstract class ComicsTracker : KeiSource() {

    // The editions most read lately, on the home page
    override suspend fun getPopularManga(page: Int): MangasPage {
        val home = client.get(baseUrl, Fetch.DOCUMENT).asJsoup().extractNextJs<HomeDto>()!!
        return MangasPage(home.topEngagement.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val recent = client.get("$baseUrl/recent", Fetch.DOCUMENT).asJsoup().extractNextJs<EditionListDto>()!!
        return MangasPage(recent.editions.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // The search needs words
        if (query.isBlank()) return getLatestUpdates(page)
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("scope", "vf")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("limit", "60")
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        return MangasPage(result.items.map { it.toSManga(baseUrl) }, result.hasMore)
    }

    // An edition (its page or its reader), a series of editions, the latest ones or a search
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        val edition = when (segments.firstOrNull()) {
            "comics" -> segments.getOrNull(1)
            "read" -> url.queryParameter("editionId")
            else -> null
        }
        return when {
            edition != null -> {
                val manga = SManga.create().apply { this.url = "/comics/$edition" }
                MangasPage(listOf(getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga), false)
            }
            segments.size == 2 && segments[0] == "vf" -> {
                val series = client.get(url, Fetch.DOCUMENT).asJsoup().extractNextJs<SeriesDto>()!!
                MangasPage(series.members.map { it.toSManga(baseUrl) }, false)
            }
            segments == listOf("recent") -> getLatestUpdates(page)
            segments == listOf("search") -> getSearchMangaList(page, url.queryParameter("q").orEmpty(), FilterList())
            else -> MangasPage(emptyList(), false)
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val edition = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().extractNextJs<EditionPageDto>()!!
        return SMangaUpdate(edition.toSManga(baseUrl, manga.url), listOf(edition.toSChapter(manga.url)))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(listUrl(chapter.memo.getString("folder")), Fetch.XHR.from(getChapterUrl(chapter)))
        // The images are signed for the edition's folder, and the list gives the signature
        val signature = response.header("x-pages-query")!!
        return response.parseAs<List<String>>()
            .filter { IMAGE.containsMatchIn(it) }
            .mapIndexed { index, key -> Page(index, imageUrl = IMAGES_URL.toHttpUrl().newBuilder().addPathSegments(key).encodedQuery(signature).build().toString()) }
    }

    // A signature lasts about a day: the pages of a list the app kept longer are signed again, once per folder
    override fun getImageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()
        if (url.isSigned()) return GET(url, headers)
        return GET(url.newBuilder().encodedQuery(signature(url.queryParameter("p")!!).encodedQuery).build(), headers)
    }

    private var lastSignature: HttpUrl? = null

    @Synchronized
    private fun signature(folder: String): HttpUrl = lastSignature?.takeIf { it.queryParameter("p") == folder && it.isSigned() }
        ?: client.newCall(GET(listUrl(folder), headers)).execute()
            .use { IMAGES_URL.toHttpUrl().newBuilder().encodedQuery(it.header("x-pages-query")!!).build() }
            .also { lastSignature = it }

    private fun HttpUrl.isSigned() = queryParameter("e")!!.toLong() * 1000 > Clock.System.now().toEpochMilliseconds() + 60_000

    private fun listUrl(folder: String) = "$baseUrl/api/r2-proxy/list".toHttpUrl().newBuilder()
        .addQueryParameter("prefix64", Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(folder.encodeToByteArray()))
        .build()

    companion object {
        private const val IMAGES_URL = "https://images.comics-tracker.net"
        private val IMAGE = Regex("""\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE)
    }
}
