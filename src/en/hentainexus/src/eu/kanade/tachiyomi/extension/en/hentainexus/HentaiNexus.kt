package eu.kanade.tachiyomi.extension.en.hentainexus

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiNexus :
    HttpSource(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    // Images on this site go through the free Jetpack Photon CDN.
    override val client = network.client.newBuilder()
        .rateLimit(1) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private fun parseResponse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .column").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                title = element.selectFirst(".card-header-title")!!.text()
                thumbnail_url = element.selectFirst(".card-image img")?.absUrl("src")
            }
        }
        val isPopularNow = response.request.url.encodedPath == POPULAR_NOW_PATH
        val hasNextPage = isPopularNow || document.selectFirst("a.pagination-next[href]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET(
        baseUrl + (if (page > 1) "/page/$page" else ""),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseResponse(response)

    override fun popularMangaRequest(page: Int): Request = if (page > 1) {
        searchMangaRequest(page - 1, "sort:popular", getFilterList())
    } else {
        GET(baseUrl + POPULAR_NOW_PATH, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseResponse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrlHost) {
                throw Exception("Unsupported url")
            }
            val id = url.pathSegments.getOrNull(1)
                ?: throw Exception("Unsupported url")
            return fetchSearchManga(page, "$PREFIX_ID_SEARCH$id", filters)
        }

        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(GET("$baseUrl/view/$id", headers)).asObservableSuccess()
                .map { MangasPage(listOf(mangaDetailsParse(it).apply { url = "/view/$id" }), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val actualPage = page + (filters.firstInstanceOrNull<OffsetPageFilter>()?.state?.toIntOrNull() ?: 0)
            if (actualPage > 1) {
                addPathSegments("page/$actualPage")
            }
            addQueryParameter("q", (combineQuery(filters) + query).trim())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseResponse(response)

    private val tagCountRegex = Regex("""\s*\([\d,]+\)$""")

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val table = document.selectFirst(".view-page-details")!!

        title = document.selectFirst("h1.title")!!.text()

        val artists = table.select("td.viewcolumn:contains(Artist) + td a").map { it.ownText() }
        val authors = table.select("td.viewcolumn:contains(Author) + td a").map { it.ownText() }
        author = (authors + artists).distinct().joinToString().takeIf { it.isNotEmpty() }
        artist = null

        description = buildString {
            listOf("Circle", "Event", "Magazine", "Parody", "Publisher", "Pages", "Favorites").forEach { key ->
                val cell = table.selectFirst("td.viewcolumn:contains($key) + td")
                cell
                    ?.ownText()
                    ?.ifEmpty { cell.selectFirst("a")?.ownText() }
                    ?.let { appendLine("$key: $it") }
            }

            table.selectFirst("td.viewcolumn:contains(Description) + td")?.text()?.let {
                appendLine()
                append(it)
            }
        }.trim()

        genre = table.select("span.tag a").joinToString {
            it.text().replace(tagCountRegex, "")
        }.takeIf { it.isNotEmpty() }

        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        status = SManga.COMPLETED

        thumbnail_url = document.selectFirst("figure.image img")?.attr("src")
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd MMMM yyyy", Locale.US)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val table = document.selectFirst(".view-page-details")!!
        val dateUploadStr = table.selectFirst("td.viewcolumn:contains(Published) + td")?.text()

        val id = response.request.url.pathSegments.last()
        return listOf(
            SChapter.create().apply {
                url = "/read/$id"
                name = "Chapter"
                date_upload = dateFormat.tryParse(dateUploadStr)
            },
        )
    }

    private fun imageFormatPref() = preferences.getString(PREF_IMAGE_FORMAT, "source")!!

    private fun imageField(format: String) = when (format) {
        "source" -> "image_source"
        "avif" -> "image_avif"
        else -> "image_fallback"
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(initReader)")?.data()
            ?: throw Exception("Could not find initReader script; the page structure may have changed")

        val encoded = script.substringAfter("initReader(\"").substringBefore("\",")
        val data = Utils.decryptData(encoded)

        val images = json.parseToJsonElement(data).jsonArray
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "image" }

        if (images.isEmpty()) {
            return emptyList()
        }

        val format = imageFormatPref()
        val field = imageField(format)

        if (images.first().jsonObject[field] == null) {
            val label = IMAGE_FORMATS[format] ?: format
            throw Exception("Selected quality '$label' is not available. Login or select another quality.")
        }

        return images.mapIndexed { i, page ->
            Page(i, imageUrl = page.jsonObject.getValue(field).jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header(
            """
            Separate items with commas (,)
            Prepend with dash (-) to exclude
            For items with multiple words, surround them with double quotes (")
            """.trimIndent(),
        ),
        TagFilter(),
        ArtistFilter(),
        AuthorFilter(),
        CircleFilter(),
        EventFilter(),
        ParodyFilter(),
        MagazineFilter(),
        PublisherFilter(),

        Filter.Separator(),
        OffsetPageFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_FORMAT
            title = "Image Quality"
            entries = IMAGE_FORMATS.values.toTypedArray()
            entryValues = IMAGE_FORMATS.keys.toTypedArray()
            summary = "%s\nOriginal quality requires a user account."
            setDefaultValue("webp")
        }.also(screen::addPreference)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val POPULAR_NOW_PATH = "/explore/hot"
        private const val PREF_IMAGE_FORMAT = "pref_image_format"

        private val IMAGE_FORMATS = linkedMapOf(
            "source" to "Original",
            "webp" to "WebP",
            "avif" to "AVIF",
        )
    }
}
