package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getObjectOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class DeviantArt :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()

    private val dateFormat by lazy { DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH) }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(30, 1.minutes) { it.host == baseUrlHost }
        rateLimit(30, 5.minutes) { it.host == backendBaseUrlHost }
        addInterceptor(OriginalImageInterceptor())
    }

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }
    private val backendBaseUrlHost by lazy { backendBaseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)

    // ============================== Search ===================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.toGalleryPath(baseUrl) ?: return getArtByUrl(url)
        val link = "$baseUrl$path"
        val document = client.get(link, Fetch.DOCUMENT).asJsoup()

        return parseGalleryDetails(document, link)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val matchGroups = requireNotNull(
            GALLERY_QUERY_REGEX.matchEntire(query)?.groupValues,
        ) { SEARCH_FORMAT_MSG }
        val username = matchGroups[1]
        val folderId = matchGroups[2].ifEmpty { "all" }
        val link = "$baseUrl/$username/gallery/$folderId"
        val document = client.get(link, Fetch.DOCUMENT).asJsoup()

        return MangasPage(listOf(parseGalleryDetails(document, link)), false)
    }

    private suspend fun getArtByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrlHost.removePrefix("www.")) return null
        if (url.pathSegments.getOrNull(1) != "art") return null

        val username = url.pathSegments[0]
        val artId = url.encodedPathSegments.getOrNull(2) ?: return null
        val document = client.get("$baseUrl/$username/art/$artId", Fetch.DOCUMENT).asJsoup()

        return parseArtDetails(document)
    }

    // ============================== Details + Chapters =======================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val link = getMangaUrl(manga)

        if (!manga.url.contains("/gallery/")) {
            val document = client.get(link, Fetch.DOCUMENT).asJsoup()
            return SMangaUpdate(
                manga = parseArtDetails(document),
                chapters = parseArtChapterList(document, link),
            )
        }

        var updatedManga = manga
        var updatedChapters = chapters

        coroutineScope {
            val mangaDeferred = if (fetchDetails) {
                async { parseGalleryDetails(client.get(link, Fetch.DOCUMENT).asJsoup(), link) }
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

        // "{artist}'s Gallery on DeviantArt" or "{folder} by {artist} on DeviantArt"
        val pageTitle = document.title().removeSuffix(" on DeviantArt")
        val isAllGallery = link.toHttpUrl().pathSegments[2] == "all"
        val artistName = pageTitle.removeSuffix("'s Gallery")
            .substringAfterLast(" by ").takeIf(String::isNotBlank)
        val galleryName = if (isAllGallery) "All" else pageTitle.substringBeforeLast(" by ")
        val artistInTitle = (preferences.artistInTitle == ArtistInTitle.ALWAYS.name) ||
            ((preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name) && isAllGallery)

        return SManga.create().apply {
            setUrlWithoutDomain(link)
            author = artistName
            title = when {
                artistInTitle && artistName != null -> "$artistName - $galleryName"
                else -> galleryName
            }
            description = gallery?.selectFirst(".legacy-journal")?.wholeText()
            thumbnail_url = gallery?.selectFirst("img[property=contentUrl]")?.getImgAttr()
                ?: document.selectFirst("#background-container + div a[data-icon]")?.attr("abs:data-icon")
            status = SManga.ONGOING
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

        val document = client.get(url, Fetch.XHR).asJsoupXml()
        val chapterList = parseToChapterList(document).toMutableList()
        var nextUrl = document.selectFirst("[rel=next]")?.absUrl("href")

        while (nextUrl != null) {
            val newDocument = client.get(nextUrl, Fetch.XHR).asJsoupXml()
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
        if (chapterList.isEmpty()) return
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

    private fun parseArtDetails(document: Document): SManga = SManga.create().apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        // Update higher resolution thumbnail
        thumbnail_url = document.selectFirst("img[property=contentUrl]")?.getImgAttr()

        // The canonical link stays clean even when the page was opened through an encoded one, which DeviantArt
        // serves without a redirect: …/art/Eye-Contact%2D1367064967 would otherwise give the ID "Contact%2D1367064967"
        val link = document.selectFirst("link[rel=canonical]")?.attr("abs:href")
            ?: ART_LINK_REGEX.find(document.location())?.groupValues?.get(1)
            ?: document.location()
        setUrlWithoutDomain(link)
        val deviationId = link.substringAfterLast('-')
        // "{title} by {artist} on DeviantArt"
        title = document.title().removeSuffix(" on DeviantArt").substringBeforeLast(" by ")

        try {
            val entities = document.parseScriptJson<StateContent>(INITIAL_STATE_REGEX)?.entities ?: return@apply
            val deviation = entities.deviation.getObjectOrNull(deviationId)?.parseAs<DeviationState>() ?: return@apply
            val user = entities.user.getObjectOrNull(deviation.author.toString())?.parseAs<Author>()
            val author = user?.username
            setUrlWithoutDomain(deviation.url)
            title = deviation.title
            deviation.media.getCoverUrl()?.let { thumbnail_url = it }
            author?.let { this.author = it }
        } catch (e: SerializationException) {
            Log.w("DeviantArt", "Error parsing JSON", e)
        }
    }

    private fun parseArtChapterList(document: Document, link: String): List<SChapter> {
        val time = document.selectFirst("span:contains(Published) time")?.attr("datetime")
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(link)
                name = "Art"
                time?.let { date_upload = DateTimeFormatter.ISO_OFFSET_DATE_TIME.tryParse(it) }
            },
        )
    }

    // ============================== Pages =====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.parsePages(url.pathSegments.last().substringAfterLast('-'))
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .tag(Page::class.java, page)
        .build()

    private fun Response.asJsoupXml(): Document = Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())

    // The JSON is embedded as a JS string literal, which may contain `\'` (valid JS, invalid JSON)
    private inline fun <reified T> Document.parseScriptJson(regex: Regex): T? {
        val literal = select("script").firstNotNullOfOrNull { regex.find(it.data()) }
            ?.groupValues?.get(1) ?: return null
        return literal.replace("\\'", "'").parseAs<String>().parseAs<T>()
    }

    private fun Element.getImgAttr(): String? = when {
        hasAttr("url") -> attr("abs:url")
        hasAttr("src") -> attr("abs:src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> null
    }

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
        private const val SEARCH_FORMAT_MSG = "Please enter a DeviantArt gallery URL, gallery:{username} or gallery:{username}/{folderId}"
        private val GALLERY_QUERY_REGEX = Regex("""gallery:([\w-]+)(?:/(\d+))?""")
        private val ART_LINK_REGEX = Regex("""(.+/art/[^/?#]+)""")
        private val INITIAL_STATE_REGEX = Regex("""window\.__INITIAL_STATE__ = JSON\.parse\(("\{.+?\}")\);""")
    }
}

private fun DateTimeFormatter.tryParse(date: String): Long = try {
    ZonedDateTime.parse(date, this).toInstant().toEpochMilli()
} catch (_: DateTimeException) {
    0L
}
