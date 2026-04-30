package eu.kanade.tachiyomi.extension.en.mangakatana

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKatana :
    HttpSource(),
    ConfigurableSource {
    override val name = "MangaKatana"

    override val baseUrl = "https://mangakatana.com"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val serverPreference = "SERVER_PREFERENCE"

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addNetworkInterceptor { chain ->
        val originalResponse = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream")) {
            val orgBody = originalResponse.body.source()
            val extension = chain.request().url.toString().substringAfterLast(".")
            val newBody = orgBody.asResponseBody("image/$extension".toMediaType())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }.build()

    private val imageArrayNameRegex = Regex("""data-src['"],\s*(\w+)""")
    private val imageUrlRegex = Regex("""'([^']*)'""")

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#book_list > div.item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("div.text > h3 > a")!!.absUrl("href"))
                title = element.selectFirst("div.text > h3 > a")!!.ownText()
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Popular (is actually alphabetical)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div#book_list > div.item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("div.text > h3 > a")!!.absUrl("href"))
                title = element.selectFirst("div.text > h3 > a")!!.ownText()
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        if (query.isNotEmpty()) {
            val type = filterList.firstInstance<TypeFilter>()
            val url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("search_by", type.toUriPart())
            return GET(url.build(), headers)
        } else {
            val url = "$baseUrl/manga/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("filter", "1")
            for (filter in filterList) {
                when (filter) {
                    is GenreList -> {
                        val includedGenres = mutableListOf<String>()
                        val excludedGenres = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.isIncluded()) {
                                includedGenres.add(it.id)
                            } else if (it.isExcluded()) {
                                excludedGenres.add(it.id)
                            }
                        }
                        if (includedGenres.isNotEmpty()) url.addQueryParameter("include", includedGenres.joinToString("_"))
                        if (excludedGenres.isNotEmpty()) url.addQueryParameter("exclude", excludedGenres.joinToString("_"))
                    }

                    is GenreInclusionMode -> url.addQueryParameter("include_mode", filter.toUriPart())

                    is SortFilter -> url.addQueryParameter("order", filter.toUriPart())

                    is StatusFilter -> {
                        if (filter.toUriPart().isNotEmpty()) {
                            url.addQueryParameter("status", filter.toUriPart())
                        }
                    }

                    is ChaptersFilter -> {
                        when (filter.state.trim()) {
                            "-1" -> url.addQueryParameter("chapters", "e1")
                            "" -> url.addQueryParameter("chapters", "1")
                            else -> url.addQueryParameter("chapters", filter.state.trim())
                        }
                    }

                    else -> {}
                }
            }
            return GET(url.build(), headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val pathSegments = response.request.url.pathSegments
        return if (pathSegments[0] == "manga" && pathSegments[1] != "page") {
            val document = response.asJsoup()
            val manga = SManga.create().apply {
                thumbnail_url = parseThumbnail(document)
                title = document.selectFirst("h1.heading")!!.text()
            }
            manga.setUrlWithoutDomain(response.request.url.toString())
            MangasPage(listOf(manga), false)
        } else {
            val document = response.asJsoup()
            val mangas = document.select("div#book_list > div.item").map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.selectFirst("div.text > h3 > a")!!.absUrl("href"))
                    title = element.selectFirst("div.text > h3 > a")!!.ownText()
                    thumbnail_url = element.selectFirst("img")!!.absUrl("src")
                }
            }
            val hasNextPage = document.selectFirst("a.next.page-numbers") != null
            MangasPage(mangas, hasNextPage)
        }
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            author = document.select(".author").eachText().joinToString()
            description = document.select(".summary > p").text() +
                (document.select(".alt_name").text().takeIf { it.isNotEmpty() }?.let { "\n\nAlt name(s): $it" } ?: "")
            status = parseStatus(document.select(".value.status").text())
            genre = document.select(".genres > a").joinToString { it.text() }
            thumbnail_url = parseThumbnail(document)
        }
    }

    private fun parseThumbnail(document: Document) = document.select("div.media div.cover img").attr("abs:src")

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("tr:has(.chapter)").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                name = element.selectFirst("a")!!.text()
                date_upload = dateFormat.tryParse(element.select(".update_time").text())
            }
        }
    }

    // Page List

    override fun pageListRequest(chapter: SChapter): Request {
        val serverSuffix = preferences.getString(serverPreference, "")?.takeIf { it.isNotBlank() }?.let { "?sv=$it" } ?: ""
        return GET(baseUrl + chapter.url + serverSuffix, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageScript = document.select("script:containsData(data-src)").firstOrNull()?.data()
            ?: return emptyList()
        val imageArrayName = imageArrayNameRegex.find(imageScript)?.groupValues?.get(1)
            ?: return emptyList()
        val imageArrayRegex = Regex("""var $imageArrayName=\[([^\[]*)]""")

        return imageArrayRegex.find(imageScript)?.groupValues?.get(1)?.let {
            imageUrlRegex.findAll(it).asIterable().mapIndexed { i, mr ->
                Page(i, imageUrl = mr.groupValues[1])
            }
        } ?: emptyList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = "server_preference"
            title = "Server preference"
            entries = arrayOf("Server 1", "Server 2", "Server 3")
            entryValues = arrayOf("", "mk", "3")
            setDefaultValue("")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue.toString()
                preferences.edit().putString(serverPreference, selected).commit()
            }
        }

        screen.addPreference(serverPref)
    }

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Other filters ignored if using text search!"),
        TypeFilter(),
        Filter.Separator(),
        GenreList(genres),
        GenreInclusionMode(),
        SortFilter(),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Input -1 to search for only oneshots"),
        ChaptersFilter(),
    )

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("MMM-dd-yyyy", Locale.US)
        }
    }
}
