package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.DEFAULT_CACHE_CONTROL
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
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
        suspend fun libraryData(refresh: Boolean): LibraryData {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .apply { libraryFormFields(refresh).forEach { (name, value) -> addFormDataPart(name, value) } }
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
            return client.post(url, Fetch.XHR.from("/biblioteca/"), body).parseAs<LibraryResponse>().data
        }

        val library = try {
            libraryData(refresh = false)
        } catch (e: HttpException) {
            if (e.code != 403) throw e
            libraryData(refresh = true)
        }
        return MangasPage(parseArchive(library.html.asJsoup(baseUrl)), library.currentPage < library.totalPages)
    }

    private val formFieldsMutex = Mutex()

    private var formFields: List<Pair<String, String>>? = null

    // Fixed fields of the library page's own request (action and token)
    private suspend fun libraryFormFields(refresh: Boolean) = formFieldsMutex.withLock {
        formFields.takeUnless { refresh } ?: client.get("$baseUrl/biblioteca/", Fetch.DOCUMENT, if (refresh) CacheControl.FORCE_NETWORK else DEFAULT_CACHE_CONTROL).asJsoup()
            .selectFirst("script:containsData(xyaoi_library_filter)")!!.data()
            .let(FORM_FIELD_REGEX::findAll)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
            .also { formFields = it }
    }

    // Cards carry hashed class names
    override fun archiveSelector() = ":has(> a[title]:has(img))"

    override val archiveUrlSelector = "a[title]:has(img)"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        title = element.selectFirst(archiveUrlSelector)!!.attr("title")
    }

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

    // Chapter rows carry hashed class names and an encoded link
    override fun chapterListSelector() = "li[data-chap-slug]"

    override fun chapterFromElement(element: Element, mangaPath: String) = SChapter.create().apply {
        url = element.attrOrNull("data-chap-slug")!!
        name = element.attrOrNull("data-chap-name")!!
        date_upload = parseChapterDate(element.selectFirst("""span:matchesOwn(^(\d{2}/\d{2}/\d{4}|.+ atrás)$)""")?.text())
        memo = buildJsonObject { put("mangaPath", mangaPath) }
    }

    override fun parsePages(document: Document): List<Page> {
        if (document.selectFirst("iframe[src*=player.php]") != null) return emptyList()
        return document.selectFirst("script[id^=xy_][id$=_d]")!!.data()
            .parseAs<ReaderData>()
            .toPageUrls()
            .mapIndexed { index, imageUrl -> Page(index, document.location(), imageUrl) }
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

    companion object {
        private val FORM_FIELD_REGEX = Regex("""formData\.append\('([^']+)',\s*'([^']*)'\)""")
    }
}
