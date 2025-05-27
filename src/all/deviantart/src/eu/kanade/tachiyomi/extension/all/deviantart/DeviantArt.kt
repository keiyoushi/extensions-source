package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class DeviantArt : HttpSource(), ConfigurableSource {
    override val name = "DeviantArt"
    override val baseUrl = "https://www.deviantart.com"
    override val lang = "all"
    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0")
    }

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()

    private val dateFormat by lazy {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    }

    private fun parseDate(dateStr: String?): Long {
        return try {
            dateFormat.parse(dateStr ?: "")!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException(SEARCH_FORMAT_MSG)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException(SEARCH_FORMAT_MSG)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val matchGroups = requireNotNull(
            Regex("""gallery:([\w-]+)(?:/(\d+))?""").matchEntire(query)?.groupValues,
        ) { SEARCH_FORMAT_MSG }
        val username = matchGroups[1]
        val folderId = matchGroups[2].ifEmpty { "all" }
        return GET("$baseUrl/$username/gallery/$folderId", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val manga = mangaDetailsParse(response)
        return MangasPage(listOf(manga), false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val gallery = document.selectFirst("#sub-folder-gallery")

        // If manga is sub-gallery then use sub-gallery name, else use gallery name
        val galleryName = gallery?.selectFirst("._2vMZg + ._2vMZg")?.text()?.substringBeforeLast(" ")
            ?: gallery?.selectFirst("[aria-haspopup=listbox] > div")!!.ownText()
        val artistInTitle = preferences.artistInTitle == ArtistInTitle.ALWAYS.name ||
            preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name && galleryName == "All"

        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            author = document.title().substringBefore(" ")
            title = when (artistInTitle) {
                true -> "$author - $galleryName"
                false -> galleryName
            }
            description = gallery?.selectFirst(".legacy-journal")?.wholeText()
            thumbnail_url = gallery?.selectFirst("img[property=contentUrl]")?.absUrl("src")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val pathSegments = getMangaUrl(manga).toHttpUrl().pathSegments
        val username = pathSegments[0]
        val query = when (val folderId = pathSegments[2]) {
            "all" -> "gallery:$username"
            else -> "gallery:$username/$folderId"
        }

        val url = backendBuilder()
            .addPathSegment("rss.xml")
            .addQueryParameter("q", query)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoupXml()
        val chapterList = parseToChapterList(document).toMutableList()
        var nextUrl = document.selectFirst("[rel=next]")?.absUrl("href")

        while (nextUrl != null) {
            val newRequest = GET(nextUrl, headers)
            val newResponse = client.newCall(newRequest).execute()
            val newDocument = newResponse.asJsoupXml()
            val newChapterList = parseToChapterList(newDocument)
            chapterList.addAll(newChapterList)

            nextUrl = newDocument.selectFirst("[rel=next]")?.absUrl("href")
        }

        return chapterList.also(::orderChapterList).toList()
    }

    private fun parseToChapterList(document: Document): List<SChapter> {
        return document.select("item").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("link")!!.text())
                name = it.selectFirst("title")!!.text()
                date_upload = parseDate(it.selectFirst("pubDate")?.text())
                scanlator = it.selectFirst("media|credit")?.text()
            }
        }
    }

    private fun orderChapterList(chapterList: MutableList<SChapter>) {
        // In Mihon's updates tab, chapters are ordered by source instead
        // of chapter number, so to avoid updates being shown in reverse,
        // disregard source order and order chronologically instead
        if (chapterList.first().date_upload < chapterList.last().date_upload) {
            chapterList.reverse()
        }
        chapterList.forEachIndexed { i, chapter ->
            chapter.chapter_number = chapterList.size - i.toFloat()
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val firstImageUrl = document.selectFirst("img[fetchpriority=high]")?.absUrl("src")
        return when (val buttons = document.selectFirst("[draggable=false]")?.children()) {
            null -> listOf(Page(0, imageUrl = firstImageUrl))
            else -> buttons.mapIndexed { i, button ->
                // Remove everything past "/v1/" to get original instead of thumbnail
                val imageUrl = button.selectFirst("img")?.absUrl("src")?.substringBefore("/v1/")
                Page(i, imageUrl = imageUrl)
            }.also {
                // First image needs token to get original, which is included in firstImageUrl
                it[0].imageUrl = firstImageUrl
            }
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private fun Response.asJsoupXml(): Document {
        return Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val artistInTitlePref = ListPreference(screen.context).apply {
            key = ArtistInTitle.PREF_KEY
            title = "Artist name in manga title"
            entries = ArtistInTitle.values().map { it.text }.toTypedArray()
            entryValues = ArtistInTitle.values().map { it.name }.toTypedArray()
            summary = "Current: %s\n\n" +
                "Changing this preference will not automatically apply to manga in Library " +
                "and History, so refresh all DeviantArt manga and/or clear database in Settings " +
                "> Advanced after doing so."
            setDefaultValue(ArtistInTitle.defaultValue.name)
        }

        screen.addPreference(artistInTitlePref)
    }

    private enum class ArtistInTitle(val text: String) {
        NEVER("Never"),
        ALWAYS("Always"),
        ONLY_ALL_GALLERIES("Only in \"All\" galleries"),
        ;

        companion object {
            const val PREF_KEY = "artistInTitlePref"
            val defaultValue = ONLY_ALL_GALLERIES
        }
    }

    private val SharedPreferences.artistInTitle
        get() = getString(ArtistInTitle.PREF_KEY, ArtistInTitle.defaultValue.name)

    companion object {
        private const val SEARCH_FORMAT_MSG = "Please enter a query in the format of gallery:{username} or gallery:{username}/{folderId}"
    }
}
