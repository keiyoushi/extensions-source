package eu.kanade.tachiyomi.extension.all.hennojin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Evaluator
import java.text.SimpleDateFormat
import java.util.Locale

class Hennojin(override val lang: String) : HttpSource() {
    override val baseUrl = "https://hennojin.com"

    override val name = "Hennojin"

    // Popular is latest
    override val supportsLatest = false

    private val httpUrl by lazy { "$baseUrl/home".toHttpUrl() }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun popularMangaRequest(page: Int) = httpUrl.request {
        when (lang) {
            "ja" -> {
                addEncodedPathSegments("page/$page/")
                addQueryParameter("archive", "raw")
            }
            else -> addEncodedPathSegments("page/$page")
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".grid-items .layer-content").map { element ->
            SManga.create().apply {
                element.selectFirst(".title_link > a")?.let {
                    title = it.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".paginate .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = httpUrl.request {
        addEncodedPathSegments("page/$page")
        addQueryParameter("keyword", query)
        addQueryParameter("_wpnonce", WP_NONCE)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            description = document.select(".manga-subtitle + p + p")
                .joinToString("\n") {
                    it
                        .apply { select(Evaluator.Tag("br")).prepend("\\n") }
                        .text()
                        .replace("\\n", "\n")
                        .replace("\n ", "\n")
                }
            genre = document.select(
                ".tags-list a[href*=/parody/]," +
                    ".tags-list a[href*=/tags/]," +
                    ".tags-list a[href*=/character/]",
            ).joinToString { it.text() }
            artist = document.selectFirst(".tags-list a[href*=/artist/]")?.text()
            author = document.selectFirst(".tags-list a[href*=/group/]")?.text() ?: artist
            status = SManga.COMPLETED
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val date = document
            .selectFirst(".manga-thumbnail > img")
            ?.absUrl("src")
            ?.let { url ->
                client.newCall(Request.Builder().url(url).head().build())
                    .execute()
                    .use { it.date }
            }

        return document.select("a:contains(Read Online)").map {
            SChapter.create().apply {
                setUrlWithoutDomain(
                    it
                        .absUrl("href")
                        .toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.removeAllQueryParameters("view")
                        ?.addQueryParameter("view", "multi")
                        ?.build()
                        ?.toString()
                        ?: it.absUrl("href"),
                )
                name = "Chapter"
                date?.let { date_upload = it }
                chapter_number = -1f
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".slideshow-container > img")
            .mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private inline fun HttpUrl.request(
        block: HttpUrl.Builder.() -> HttpUrl.Builder,
    ) = GET(newBuilder().block().build(), headers)

    private inline val Response.date: Long
        get() = headers["Last-Modified"]?.let { httpDate.tryParse(it) } ?: 0L

    companion object {
        // Let's hope this doesn't change
        private const val WP_NONCE = "40229f97a5"

        private val httpDate by lazy {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        }
    }
}
