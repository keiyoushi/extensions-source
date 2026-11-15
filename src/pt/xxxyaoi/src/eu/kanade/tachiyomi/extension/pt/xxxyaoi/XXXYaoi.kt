package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : MadaraNoAjax() {

    override val supportsPostId = false

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override fun Headers.Builder.configureHeaders() = set("Sec-GPC", "1")
        .set("Pragma", "no-cache")

    override fun getHomeUrl() = "$baseUrl/biblioteca/"

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = libraryPage(page, "latest")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = libraryPage(
        page = page,
        orderby = filters.firstInstance<SortFilter>().value,
        search = query,
        status = filters.firstInstance<StatusFilter>().value,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty(),
    )

    private suspend fun libraryPage(page: Int, orderby: String, search: String = "", status: String = "", genre: String = ""): MangasPage {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("search", search)
            .addFormDataPart("status", status)
            .addFormDataPart("genre", genre)
            .addFormDataPart("orderby", orderby)
            .addFormDataPart("page", page.toString())
            .build()
        // Action in the query: Cloudflare's challenge WebView reloads this url without the form body
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("wp-admin/admin-ajax.php")
            .addQueryParameter("action", "xyaoi_library_filter")
            .build()
        val library = client.post(url, Fetch.XHR.from("/biblioteca/"), body).parseAs<LibraryResponse>().data
        return MangasPage(parseArchive(library.html.asJsoup(baseUrl)), library.currentPage < library.totalPages)
    }

    override fun archiveSelector() = ".xyaoi-card"

    override val archiveUrlSelector = "h3 > a"

    override val mangaDetailsSelectorTitle = "h1"
    override val mangaDetailsSelectorAuthor = "a[href*=author]"
    override val mangaDetailsSelectorArtist = "a[href*=artist]"
    override val mangaDetailsSelectorStatus = "span:contains(status) + span"
    override val mangaDetailsSelectorDescription = ".xyaoi-synopsis-text"
    override val mangaDetailsSelectorThumbnail = ".xyaoi-cover-img"

    // Genre links point at the library's ?genre= filter, not a genre route
    override val mangaDetailsSelectorTag = "a[rel=tag][href*='?genre=']"
    override val seriesTypeSelector = "span:contains(tipo) + span:not(:contains(Não informado))"

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(mangaDetailsSelectorDescription))
            .field("Lançamento", document.property("LANÇAMENTO"))
            .field("Classificação", document.property("CLASSIFICAÇÃO"))
            .field("Info", document.property("INFO"))
            .build()
    }

    override fun parseChapterList(document: Document, mangaPath: String): List<SChapter> {
        val scan = document.property("SCAN")
        return super.parseChapterList(document, mangaPath).onEach { it.scanlator = scan }
    }

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        name = element.selectFirst("div > span:nth-child(1)")!!.text()
        date_upload = parseChapterDate(element.selectFirst("div:has(> span:nth-child(1)) + div")?.text())
    }

    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val html = client.get(chapterUrl, Fetch.DOCUMENT).body.string()
        val rendered = try {
            runWebView<String> {
                userAgent = headers["User-Agent"].orEmpty()
                // A script load error makes the site's ad check delete the decoded pages
                interceptRequest { request ->
                    if (request.isForMainFrame) null else WebResourceResponse("text/javascript", null, 200, "OK", null, ByteArrayInputStream(ByteArray(0)))
                }
                onPageFinished {
                    evaluateJs("document.documentElement.outerHTML") { resolve(it.parseAs<String>()) }
                }
                loadData(chapterUrl, html)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        return rendered.asJsoup(chapterUrl)
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".related-reading-wrap")
        .mapNotNull { element ->
            val link = element.selectFirst("h5 a") ?: return@mapNotNull null
            SManga.create().apply {
                url = link.absUrl("href").toHttpUrl().encodedPath
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.let(::imageFromElement)
            }
        }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>().orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/biblioteca/", Fetch.DOCUMENT).asJsoup()
        .select(".xyaoi-genre-item[data-genre]")
        .mapNotNull { element ->
            val slug = element.attrOrNull("data-genre") ?: return@mapNotNull null
            Genre(element.selectFirst(".xyaoi-genre-name")!!.text(), slug)
        }
        .toJsonElement()

    private fun Document.property(label: String) = selectFirst(".xyaoi-prop-label:containsOwn($label) + .xyaoi-prop-value:not(:containsOwn(Não informado))")?.textOrNull()
}
