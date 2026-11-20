package eu.kanade.tachiyomi.extension.es.kumanga

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@Source
abstract class Kumanga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private val redirectClient by lazy { client.newBuilder().followRedirects(false).build() }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".update_item h4 a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.text()
                thumbnail_url = coverUrl(url.toMangaId())
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("mangalist")
            filters.filterIsInstance<CheckBoxGroup>().forEach { group ->
                val values = group.state.filter { it.state }.joinToString("-") { it.value }
                if (values.isNotEmpty()) addQueryParameter(group.param, values)
            }
            if (query.isNotBlank()) addQueryParameter("keywords", query.trim())
            addQueryParameter("page", page.toString())
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("li.km-li-crd").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("onclick").substringAfter("'").substringBefore("'"))
                title = element.selectFirst(".km-title-p-card")!!.text()
                thumbnail_url = element.selectFirst("img.km-img-crd")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".section-nav-pagination a[href]:has(.fa-chevron-right)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val segment = url.pathSegments.getOrNull(1) ?: return null
        val mangaUrl = when {
            segment == "c" || segment == "leer" ->
                client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("a[href~=manga/\\d+/]")?.absUrl("href")?.toHttpUrl() ?: return null
            segment.toIntOrNull() != null -> baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").addPathSegment(segment).build()
            else -> return null
        }
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return parseDetails(document).apply {
            setUrlWithoutDomain(document.selectFirst("meta[property=og:url]")!!.absUrl("content"))
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = parseDetails(document).apply { url = manga.url }
        return SMangaUpdate(details, parseChapters(document, manga.url.toMangaId()))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.media-name__main")!!.ownText()
        thumbnail_url = document.selectFirst("img.lazy-loaded")?.absUrl("data-src")
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#idesc")?.textOrNull()?.takeIf { it != "Sin descripción." })
            .altTitles(document.selectFirst(".media-name__alt")?.text()?.split(", ").orEmpty(), "Nombres alternativos")
            .build()
        genre = (listOfNotNull(document.infoValue("Tipo")) + document.select(".media-cats a").map { it.text() })
            .joinToString().ifEmpty { null }
        status = when (document.infoValue("Estado")?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "abandonado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.infoValue(title: String) = selectFirst(".media-info-list__item:has(.media-info-list__title:contains($title)) .media-info-list__value")?.text()

    private fun parseChapters(document: Document, mangaId: Int): List<SChapter> {
        val listed = document.select(".media-chapters-list a.media-chapter__link").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = buildString {
                    append(element.selectFirst(".media-chapter__name strong")!!.text())
                    element.selectFirst(".media-chapter__name .no_mobil")?.textOrNull()?.let { append(": ", it) }
                }
            }
        }
        val others = document.selectFirst("script:containsData(OTHER_CHAPTERS)")?.data()
            ?.let { OTHER_CHAPTERS_REGEX.find(it)!!.groupValues[1].parseAs<List<OtherChapterDto>>() }
            .orEmpty()
            .map { it.toSChapter(mangaId) }
        return listed + others
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val readerUrl = if (chapter.url.startsWith("/manga/leer/")) {
            getChapterUrl(chapter)
        } else {
            // Chapters redirect to their upload (/manga/c/<id>), or list one per group; the reader (/manga/leer/<id>) shares the id
            val upload = redirectClient.get(getChapterUrl(chapter), Fetch.DOCUMENT, ensureSuccess = false).use { response ->
                response.header("Location") ?: run {
                    if (!response.isSuccessful) throw HttpException(response.code)
                    response.asJsoup().selectFirst("a.readButton")?.absUrl("href") ?: return emptyList()
                }
            }
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("manga/leer")
                .addPathSegment(baseUrl.toHttpUrl().resolve(upload)!!.pathSegments.last())
                .build()
                .toString()
        }
        val html = client.get(readerUrl, Fetch.DOCUMENT).body.string()
        val readerHost = readerUrl.toHttpUrl().host
        // The site keeps renaming its page list variable: read the list the reader's x-for template loops over
        val paths = try {
            runWebView<List<String>> {
                interceptRequest { request ->
                    if (request.url.host == readerHost && request.url.path.orEmpty().endsWith(".js")) {
                        proxyRequest(request, client)
                    } else {
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                onPageFinished {
                    evaluateJs(PAGES_SCRIPT) { result ->
                        result.parseAs<List<String>?>()?.takeIf { it.isNotEmpty() }?.let(::resolve) ?: reject(Exception("No page list"))
                    }
                }
                loadData(readerUrl, html)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        val base = readerUrl.toHttpUrl()
        return paths.mapIndexed { index, path ->
            val url = base.resolve(path)!!
            // The site's image proxy carries the image url hex-encoded in src
            val image = url.queryParameter("src")?.takeIf(HEX_REGEX::matches)?.decodeHex()?.decodeToString()?.toHttpUrlOrNull() ?: url
            Page(index, imageUrl = image.toString())
        }
    }

    override fun getImageRequest(page: Page): Request {
        val url = page.imageUrl!!.toHttpUrl()
        if (!url.host.endsWith(".googleusercontent.com")) return super.getImageRequest(page)
        // googleusercontent answers 429 to the site's Referer
        return Request.Builder().url(url).headers(headers.newBuilder().removeAll("Referer").build()).build()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    private fun String.toMangaId() = baseUrl.toHttpUrl().resolve(this)!!.pathSegments[1].toInt()

    companion object {
        private const val PAGES_SCRIPT = """
            (() => {
                const loop = [...document.querySelectorAll('template[x-for]')].find((t) => t.content.querySelector('img'));
                const list = loop
                    .getAttribute('x-for')
                    .split(/\s+(?:in|of)\s+/)
                    .pop()
                    .trim();
                const data = new Function(`return ${'$'}{loop.closest('[x-data]').getAttribute('x-data')}`)();
                return data[list];
            })()
        """
        private val HEX_REGEX = Regex("(?:[0-9A-Fa-f]{2})+")
        private val OTHER_CHAPTERS_REGEX = Regex("""OTHER_CHAPTERS\s*=\s*(\[.*])\s*;""", RegexOption.DOT_MATCHES_ALL)
    }
}
