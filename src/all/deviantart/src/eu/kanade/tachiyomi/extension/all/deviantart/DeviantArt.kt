package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DeviantArt :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()

    private val dateFormat by lazy { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH) }

    override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)

    // ============================== Search ===================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val username = url.pathSegments.getOrNull(0) ?: return null
        val folderId = url.pathSegments.getOrNull(2) ?: return null
        val link = "$baseUrl/$username/gallery/$folderId"
        val document = client.get(link).asJsoup()

        return parseGalleryDetails(document, link)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val matchGroups = requireNotNull(
            GALLERY_QUERY_REGEX.matchEntire(query)?.groupValues,
        ) { SEARCH_FORMAT_MSG }
        val username = matchGroups[1]
        val folderId = matchGroups[2].ifEmpty { "all" }
        val link = "$baseUrl/$username/gallery/$folderId"
        val document = client.get(link).asJsoup()

        return MangasPage(listOf(parseGalleryDetails(document, link)), false)
    }

    // ============================== Details + Chapters =======================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val link = getMangaUrl(manga)

        var updatedManga = manga
        var updatedChapters = chapters

        coroutineScope {
            val mangaDeferred = if (fetchDetails) {
                async { parseGalleryDetails(client.get(link).asJsoup(), link) }
            } else {
                null
            }

            val chaptersDeferred = if (fetchChapters) {
                async { fetchGalleryChapterList(manga) }
            } else {
                null
            }

            mangaDeferred?.let { updatedManga = it.await() }
            chaptersDeferred?.let { updatedChapters = it.await() }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseGalleryDetails(document: Document, link: String): SManga {
        val gallery = document.selectFirst("#content")

        // If manga is sub-gallery then use sub-gallery name, else use gallery name
        val galleryName = gallery?.selectFirst(".DWReDc")?.ownText()
            ?: gallery?.selectFirst("[aria-haspopup=listbox] > div")!!.ownText()
        val artistInTitle = (preferences.artistInTitle == ArtistInTitle.ALWAYS.name) ||
            ((preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name) && (galleryName == "All"))

        return SManga.create().apply {
            setUrlWithoutDomain(link)
            author = document.title().substringBefore(" ")
            title = when {
                artistInTitle -> "$author - $galleryName"
                else -> galleryName
            }
            description = gallery.selectFirst(".legacy-journal")?.wholeText()
            thumbnail_url = gallery.selectFirst("img[property=contentUrl]")?.absUrl("src")
        }
    }

    private suspend fun fetchGalleryChapterList(manga: SManga): List<SChapter> {
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

        val document = client.get(url).asJsoupXml()
        val chapterList = parseToChapterList(document).toMutableList()
        var nextUrl = document.selectFirst("[rel=next]")?.absUrl("href")

        while (nextUrl != null) {
            val newDocument = client.get(nextUrl).asJsoupXml()
            chapterList.addAll(parseToChapterList(newDocument))

            nextUrl = newDocument.selectFirst("[rel=next]")?.absUrl("href")
        }

        return chapterList.also(::orderChapterList).toList()
    }

    private fun parseToChapterList(document: Document): List<SChapter> = document.select("item").map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.selectFirst("link")!!.text())
            name = it.selectFirst("title")!!.text()
            date_upload = it.selectFirst("pubDate")?.text()?.let(dateFormat::tryParse) ?: 0L
            scanlator = it.selectFirst("media|credit")?.text()
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

    // ============================== Pages =====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val buttons = document.selectFirst("[draggable=false]")?.children()
        return if (buttons == null) {
            val imageUrl = document.selectFirst("img[fetchpriority=high]")?.absUrl("src")
            listOf(Page(0, imageUrl = imageUrl))
        } else {
            buttons.mapIndexed { i, button ->
                // Remove everything past "/v1/" to get original instead of thumbnail
                // But need to preserve the query parameter where the token is
                val imageUrl = button.selectFirst("img")?.absUrl("src")
                    ?.replaceFirst(IMAGE_ORIGINAL_URL_REGEX, "")
                Page(i, imageUrl = imageUrl)
            }
        }
    }

    private fun Response.asJsoupXml(): Document = Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())

    // ============================== Settings ===================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val artistInTitlePref = ListPreference(screen.context).apply {
            key = ArtistInTitle.PREF_KEY
            title = "Artist name in manga title"
            entries = ArtistInTitle.entries.map { it.text }.toTypedArray()
            entryValues = ArtistInTitle.entries.map { it.name }.toTypedArray()
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
        private val GALLERY_QUERY_REGEX = Regex("""gallery:([\w-]+)(?:/(\d+))?""")
        private val IMAGE_ORIGINAL_URL_REGEX = Regex("""/v1(/.*)?(?=\?)""")
    }
}

private fun DateTimeFormatter.tryParse(date: String): Long = try {
    ZonedDateTime.parse(date, this).toInstant().toEpochMilli()
} catch (_: DateTimeException) {
    0L
}
