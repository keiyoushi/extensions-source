package eu.kanade.tachiyomi.extension.en.mangakatana

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
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaKatana :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()
    private val serverPreference = "SERVER_PREFERENCE"

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor { chain ->
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
    }

    private val imageArrayNameRegex = Regex("""data-src['"],\s*(\w+)""")
    private val imageUrlRegex = Regex("""'([^']*)'""")

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/page/$page").asJsoup()
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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/manga/page/$page").asJsoup()
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty()) {
            val type = filters.firstInstance<TypeFilter>()
            "$baseUrl/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("search_by", type.toUriPart())
                .build()
        } else {
            "$baseUrl/manga/page/$page".toHttpUrl().newBuilder().apply {
                addQueryParameter("filter", "1")
                filters.forEach { filter ->
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
                            if (includedGenres.isNotEmpty()) addQueryParameter("include", includedGenres.joinToString("_"))
                            if (excludedGenres.isNotEmpty()) addQueryParameter("exclude", excludedGenres.joinToString("_"))
                        }

                        is GenreInclusionMode -> addQueryParameter("include_mode", filter.toUriPart())

                        is SortFilter -> addQueryParameter("order", filter.toUriPart())

                        is StatusFilter -> {
                            if (filter.toUriPart().isNotEmpty()) {
                                addQueryParameter("status", filter.toUriPart())
                            }
                        }

                        is ChaptersFilter -> {
                            when (filter.state.trim()) {
                                "-1" -> addQueryParameter("chapters", "e1")
                                "" -> addQueryParameter("chapters", "1")
                                else -> addQueryParameter("chapters", filter.state.trim())
                            }
                        }

                        else -> {}
                    }
                }
            }
                .build()
        }

        val response = client.get(url)
        val pathSegments = response.request.url.pathSegments
        val document = response.asJsoup()
        return if (pathSegments[0] == "manga" && pathSegments[1] != "page") {
            val manga = SManga.create().apply {
                thumbnail_url = parseThumbnail(document)
                title = document.selectFirst("h1.heading")!!.text()
            }
            manga.setUrlWithoutDomain(response.request.url.toString())
            MangasPage(listOf(manga), false)
        } else {
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsFromDocument(document), chapterListFromDocument(document))
    }

    private fun mangaDetailsFromDocument(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.heading")!!.text()
        author = document.select(".author").eachText().joinToString()
        description = document.select(".summary > p").text() +
            (document.select(".alt_name").text().takeIf { it.isNotEmpty() }?.let { "\n\nAlt name(s): $it" } ?: "")
        status = parseStatus(document.selectFirst(".value.status")?.text())
        genre = document.select(".genres > a").joinToString { it.text() }
        thumbnail_url = parseThumbnail(document)
    }

    private fun parseThumbnail(document: Document) = document.selectFirst("div.media div.cover img")?.attr("abs:src")

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    private fun chapterListFromDocument(document: Document): List<SChapter> = document.select("tr:has(.chapter)").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            name = element.selectFirst("a")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".update_time")?.text())
        }
    }

    // Deep link

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 2) return null
        val mangaUrl = "$baseUrl/manga/${url.pathSegments[1]}"
        val document = client.get(mangaUrl).asJsoup()
        return mangaDetailsFromDocument(document).apply {
            setUrlWithoutDomain(mangaUrl)
        }
    }

    // Page List

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val serverSuffix = preferences.getString(serverPreference, "")?.takeIf { it.isNotBlank() }?.let { "?sv=$it" } ?: ""
        val document = client.get(getChapterUrl(chapter) + serverSuffix).asJsoup()
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

    override fun getFilterList(data: JsonElement?) = FilterList(
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
        private val dateFormat = DateTimeFormatter.ofPattern("MMM-dd-yyyy", Locale.US)
    }
}
