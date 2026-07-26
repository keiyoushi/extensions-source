package eu.kanade.tachiyomi.extension.en.hentainexus

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.array
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiNexus :
    KeiSource(),
    ConfigurableSource {
    private val baseUrlHost = baseUrl.toHttpUrl().host

    private val preferences: SharedPreferences by getPreferencesLazy()

    // Images on this site go through the free Jetpack Photon CDN.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(1) { it.host == baseUrlHost }

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

    private fun parseMangaResponse(response: Response): SMangaUpdate {
        val document = response.asJsoup()
        val table = document.selectFirst(".view-page-details")!!

        val manga = SManga.create().apply {
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

        val dateUploadStr = table.selectFirst("td.viewcolumn:contains(Published) + td")?.text()

        val id = response.request.url.pathSegments.last()

        val chapters = listOf(
            SChapter.create().apply {
                url = "/read/$id"
                name = "Chapter"
                date_upload =
                    runCatching {
                        LocalDate.parse(dateUploadStr, dateFormat).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                    }.getOrDefault(0L)
            },
        )

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseResponse(client.get(baseUrl + (if (page > 1) "/page/$page" else "")))

    override suspend fun getPopularManga(page: Int): MangasPage = if (page > 1) {
        getSearchMangaList(page - 1, "sort:popular", getFilterList())
    } else {
        parseResponse(client.get(baseUrl + POPULAR_NOW_PATH))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        MangasPage(
            listOf(
                parseMangaResponse(
                    client.get("$baseUrl/view/$id"),
                ).manga.apply { url = "/view/$id" },
            ),
            false,
        )
    } else {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val actualPage = page + (filters.firstInstanceOrNull<OffsetPageFilter>()?.state?.toIntOrNull() ?: 0)
            if (actualPage > 1) {
                addPathSegments("page/$actualPage")
            }
            addQueryParameter("q", (combineQuery(filters) + query).trim())
        }.build()
        parseResponse(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1)
            ?: throw Exception("Unsupported url")
        return parseMangaResponse(
            client.get("$baseUrl/view/$id"),
        ).manga.apply { this.url = "/view/$id" }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = parseMangaResponse(client.get("$baseUrl${manga.url}"))

    private val tagCountRegex = Regex("""\s*\([\d,]+\)$""")

    private val dateFormat by lazy {
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
    }

    private fun imageFormatPref() = preferences.getString(PREF_IMAGE_FORMAT, "webp")!!

    private fun imageField(format: String) = when (format) {
        "source" -> "image_source"
        "avif" -> "image_avif"
        else -> "image_fallback"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

        val script = document.selectFirst("script:containsData(initReader)")?.data()
            ?: throw Exception("Could not find initReader script; the page structure may have changed")

        val encoded = script.substringAfter("initReader(\"").substringBefore("\",")
        val data = Utils.decryptData(encoded)

        val images = data.parseAs<JsonElement>().array
            .filter { it["type"]?.string == "image" }

        if (images.isEmpty()) {
            return emptyList()
        }

        val format = imageFormatPref()
        val field = imageField(format)

        if (images.first()[field] == null) {
            val label = IMAGE_FORMATS[format] ?: format
            throw Exception("Selected quality '$label' is not available. Login or select another quality.")
        }

        return images.mapIndexed { i, page ->
            Page(i, imageUrl = page[field]!!.string)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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
