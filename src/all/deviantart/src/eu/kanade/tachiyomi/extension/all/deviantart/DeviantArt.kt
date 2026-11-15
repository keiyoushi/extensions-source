package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.JsonElement
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

    private val baseUrlHost get() = baseUrl.toHttpUrl().host
    private val backendBaseUrlHost get() = backendBaseUrl.toHttpUrl().host

    private fun queryBuilder(page: Int): HttpUrl.Builder = backendBuilder()
        .addPathSegment("rss.xml")
        .addQueryParameter("type", "deviation")
        .apply {
            if (page > 1) {
                addQueryParameter("offset", ((page - 1) * 60).toString())
            }
        }

    // ============================== Popular ================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = queryBuilder(page)
            .apply {
                catalogSearchQuery.takeIf { it.isNotBlank() }?.let { addEncodedQueryParameter("q", it) }
            }
            .build()

        return parseMangaListPage(client.get(url, Fetch.XHR))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoupXml()
        val mangaList = parseToMangaList(document)
        val nextUrl = document.selectFirst("[rel=next]")?.absUrl("href")

        return MangasPage(mangaList, nextUrl != null)
    }

    private fun parseToMangaList(document: Document): List<SManga> {
        val items = document.select("item")
        return items.map { item ->
            val creator = item.selectFirst("media|credit")?.text()
            val artName = item.selectFirst("title")!!.text()
            val link = item.selectFirst("link")!!.text()
            val id = link.removeSuffix("/")
                .substringAfterLast('/')
                .substringAfterLast('-')

            SManga.create().apply {
                setUrlWithoutDomain(link)
                title = artTitle(artName, id)
                author = creator
                thumbnail_url = item.selectFirst("media|content")?.getImgAttr()
                author?.let { genre = "gallery:$it, by:$it" }
                description = DESCRIPTION_GUIDE
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
    }

    // ============================== Search ===================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.toGalleryPath(baseUrl) ?: return getArtByUrl(url)
        val link = "$baseUrl$path"
        val document = client.get(link, Fetch.DOCUMENT).asJsoup()

        return parseGalleryDetails(document, link)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val matchGroups = GALLERY_QUERY_REGEX.matchEntire(query.trim())?.groupValues
        if (matchGroups != null) {
            val username = matchGroups[1]
            val folderId = matchGroups[2].ifEmpty { "all" }
            val link = "$baseUrl/$username/gallery/$folderId"
            val document = client.get(link, Fetch.DOCUMENT).asJsoup()

            return MangasPage(listOf(parseGalleryDetails(document, link)), false)
        }

        val url = queryBuilder(page)
            .addEncodedQueryParameter("q", query.trim())
            .build()

        return parseMangaListPage(client.get(url, Fetch.XHR))
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
            description = listOfNotNull(
                gallery?.selectFirst(".legacy-journal")?.wholeText(),
                DESCRIPTION_GUIDE,
            ).joinToString("\n\n")
            thumbnail_url = gallery?.selectFirst("img[property=contentUrl]")?.getImgAttr()
                ?: document.selectFirst("#background-container + div a[data-icon]")?.attr("abs:data-icon")
            artistName?.let { genre = "gallery:$it, by:$it" }
            status = SManga.ONGOING
        }
    }

    private suspend fun fetchGalleryChapterList(manga: SManga): List<SChapter> {
        val pathSegments = getMangaUrl(manga).toHttpUrl().pathSegments
        val username = pathSegments[0]
        val folderId = pathSegments[2]
        val query = when {
            folderId == "all" -> "gallery:$username"
            (folderId.toLongOrNull() ?: 0) > 0 -> "gallery:$username/$folderId"
            // A DeviantArt folder other than "All" is linked by name, e.g. "Scraps" at /{username}/gallery/scraps,
            // and the feed has no query for it: gallery:{username}/scraps and gallery:{username}/-2 both return
            // the whole gallery, so listing it from the feed would show the wrong arts.
            // TODO: list it from the folder page's state instead. Its stream (`folder-deviations-gallery-{ID}`,
            //  e.g. `folder-deviations-gallery--2`, read with StateContent.folderDeviationIds) only holds the first
            //  items, see FolderDeviationStream.hasMore
            //  or use DeviantArt's internal gallery API (/_puppy/dashared/gallection/contents, folder -2) for the scraps chapter list.
            // Until then, no chapters rather than an error, so the gallery's details still refresh:
            // Chapters of the "{folderId}" folder are not implemented: DeviantArt's feed has no query for it
            else -> return emptyList()
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
        val artName = it.selectFirst("title")!!.text()
        val link = it.selectFirst("link")!!.text()
        val id = link.removeSuffix("/")
            .substringAfterLast('/')
            .substringAfterLast('-')
        // The feed gives a video only thumbnails, no media:content
        val isVideo = it.selectFirst("media|content") == null

        SChapter.create().apply {
            setUrlWithoutDomain(link)
            name = artTitle(if (isVideo) "$artName (video)" else artName, id)
            date_upload = it.selectFirst("pubDate")?.text()?.let(dateFormat::tryParse) ?: 0L
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
        description = DESCRIPTION_GUIDE

        // The canonical link stays clean even when the page was opened through an encoded one, which DeviantArt
        // serves without a redirect: …/art/Eye-Contact%2D1367064967 would otherwise give the ID "Contact%2D1367064967"
        val link = document.selectFirst("link[rel=canonical]")?.attr("abs:href")
            ?: ART_LINK_REGEX.find(document.location())?.groupValues?.get(1)
            ?: document.location()
        setUrlWithoutDomain(link)
        val deviationId = link.substringAfterLast('-')
        // "{title} by {artist} on DeviantArt"
        title = artTitle(document.title().removeSuffix(" on DeviantArt").substringBeforeLast(" by "), deviationId)

        try {
            val entities = document.parseScriptJson<StateContent>(INITIAL_STATE_REGEX)?.entities ?: return@apply
            val deviation = entities.deviation.getObjectOrNull(deviationId)?.parseAs<DeviationState>() ?: return@apply
            val user = entities.user.getObjectOrNull(deviation.author.toString())?.parseAs<Author>()
            val author = user?.username
            setUrlWithoutDomain(deviation.url)
            title = artTitle(deviation.title, deviationId)
            deviation.media.getCoverUrl()?.let { thumbnail_url = it }
            author?.let {
                this.author = it
                genre = "gallery:$it, by:$it"
            }
            if (deviation.isVideo) {
                // A mature video only lists its mp4 for a logged-in viewer
                val videoLink = deviation.media.getVideoUrl()?.let { "[Direct video link]($it)" }
                    ?: "[Log in via WebView to see this video link]".takeIf { deviation.isMature }
                videoLink?.let { description = "$it\n\n$DESCRIPTION_GUIDE" }
            }
        } catch (e: SerializationException) {
            Log.w("DeviantArt", "Error parsing JSON", e)
        }
    }

    private fun parseArtChapterList(document: Document, link: String): List<SChapter> {
        val time = document.selectFirst("span:contains(Published) time")?.attr("datetime")
        val isVideo = document.deviationState(link.removeSuffix("/").substringAfterLast('-'))?.isVideo == true
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(link)
                name = if (isVideo) "Video" else "Art"
                time?.let { date_upload = DateTimeFormatter.ISO_OFFSET_DATE_TIME.tryParse(it) }
            },
        )
    }

    // ============================== Related ===================================

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val link = getMangaUrl(manga)
        val document = client.get(link, Fetch.DOCUMENT).asJsoup()

        if (manga.url.contains("/gallery/")) {
            // Return other galleries of this user, from the page's state
            val artistName = document.title().removeSuffix(" on DeviantArt").removeSuffix("'s Gallery")
                .substringAfterLast(" by ").takeIf(String::isNotBlank)
            val username = link.toHttpUrl().pathSegments[0]
            return try {
                val state = document.parseScriptJson<StateContent>(INITIAL_STATE_REGEX) ?: return emptyList()
                val folders = state.entities.galleryFolder
                state.galleryFolderIds?.items.orEmpty().mapNotNull { folders[it.toString()] }.map { folder ->
                    val isAllGallery = folder.specialType == "all"
                    val artistInTitle = preferences.artistInTitle == ArtistInTitle.ALWAYS.name ||
                        (preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name && isAllGallery)
                    // DeviantArt's own folders have negative IDs, and their links use the lowercased name
                    // instead of the ID: "All" (-1) is /{username}/gallery/all and "Scraps" (-2) is
                    // /{username}/gallery/scraps. Going by the sign rather than a list of names covers any other one
                    // Example: https://www.deviantart.com/arven92/gallery/scraps
                    val folderPath = if (folder.folderId < 0) folder.name.lowercase() else folder.folderId.toString()

                    SManga.create().apply {
                        url = "/$username/gallery/$folderPath"
                        title = when {
                            artistInTitle && !artistName.isNullOrBlank() -> "$artistName - ${folder.name}"
                            else -> folder.name
                        }
                        // Decode each cover on its own, so one that doesn't decode only costs that folder its cover
                        thumbnail_url = folder.thumb?.let { state.entities.deviation.getObjectOrNull(it.toString()) }
                            ?.let { thumb ->
                                try {
                                    thumb.parseAs<DeviationState>().media.getCoverUrl()
                                } catch (e: SerializationException) {
                                    Log.w("DeviantArt", "Error parsing the cover of folder ${folder.folderId}", e)
                                    null
                                }
                            }
                        author = artistName
                        author?.let { genre = "gallery:$it, by:$it" }
                        description = DESCRIPTION_GUIDE
                    }
                }
            } catch (e: SerializationException) {
                Log.e("DeviantArt", "Error parsing JSON", e)
                emptyList()
            }
        } else {
            // Art
            return try {
                document.parseScriptJson<RCacheContent>(RCACHE_REGEX)?.relatedContent?.relatedContent
                    ?.mapNotNull { element ->
                        // Parse each entry on its own so one unexpected entry doesn't drop the rest
                        val related = try {
                            element.parseAs<Related>()
                        } catch (e: SerializationException) {
                            Log.e("DeviantArt", "Error parsing related content", e)
                            return@mapNotNull null
                        }
                        val contentType = related.contentType
                            ?: return@mapNotNull null

                        when (contentType) {
                            "gallery", "boosted", "recommended",
                            "monetization_promotion_tier_thirdparty",
                            "monetization_promotion_dreamup_thirdparty",
                            "monetization_promotion_pcp_firstparty",
                            -> {
                                related.deviations
                            }
                            "users" -> {
                                related.users
                                    ?.flatMap { user -> user.deviations }
                            }
                            "collections" -> {
                                related.collections
                                    ?.flatMap { collection -> collection.deviations }
                            }
                            // Group profiles only, no deviations to show
                            "groups" -> null
                            else -> {
                                Log.w("DeviantArt", "contentType: $contentType")
                                null
                            }
                        }
                    }?.flatten()
                    ?.map { deviation ->
                        SManga.create().apply {
                            setUrlWithoutDomain(deviation.url)
                            title = artTitle(deviation.title, deviation.url.substringAfterLast('-'))
                            thumbnail_url = deviation.media.getCoverUrl()
                            author = deviation.author.username
                            author?.let { genre = "gallery:$it, by:$it" }
                            description = DESCRIPTION_GUIDE
                            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                        }
                    }
                    ?: emptyList()
            } catch (e: SerializationException) {
                Log.e("DeviantArt", "Error parsing JSON", e)
                emptyList()
            }
        }
    }

    // ============================== Pages =====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val deviationId = url.pathSegments.last().substringAfterLast('-')
        val deviation = document.deviationState(deviationId)
        if (deviation == null || !deviation.isVideo) return document.parsePages(deviationId)

        // The reader can't play the video, and pages without an image fail to download, so only show its poster
        return listOf(createImagePage(0, deviation.media.getOriginalUrl()))
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

    private fun Document.deviationState(deviationId: String): DeviationState? = try {
        parseScriptJson<StateContent>(INITIAL_STATE_REGEX)?.entities?.deviation
            ?.getObjectOrNull(deviationId)?.parseAs<DeviationState>()
    } catch (e: SerializationException) {
        Log.w("DeviantArt", "Error parsing JSON", e)
        null
    }

    private fun Element.getImgAttr(): String? = when {
        hasAttr("url") -> attr("abs:url")
        hasAttr("src") -> attr("abs:src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> null
    }

    private fun artTitle(name: String, id: String): String = if (preferences.includeArtIdInChapterName) "$name [$id]" else name

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

        SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_NAME_PREF
            title = "Include Art ID in art titles and chapter names (avoids duplicate download names)"
            summaryOff = "Title only"
            summaryOn = "Title [ID]"
            setDefaultValue(false)
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = CATALOG_SEARCH_QUERY_PREF
            title = CATALOG_FORMAT_MSG
            summary = CATALOG_SEARCH_QUERY + (preferences.getString(CATALOG_SEARCH_QUERY_PREF, "") ?: "")
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                summary = CATALOG_SEARCH_QUERY + newValue
                true
            }
        }.also(screen::addPreference)
    }

    private enum class ArtistInTitle(val text: String) {
        NEVER("Never"),
        ALWAYS("Always"),
        ONLY_ALL_GALLERIES("Only in \"All\" galleries"),
        ;

        companion object {
            const val PREF_KEY = "artistInTitlePref"
            val defaultValue = ALWAYS
        }
    }

    private val SharedPreferences.artistInTitle
        get() = getString(ArtistInTitle.PREF_KEY, ArtistInTitle.defaultValue.name)

    private val SharedPreferences.includeArtIdInChapterName
        get() = getBoolean(CHAPTER_NAME_PREF, false)

    private val catalogSearchQuery
        get() = preferences.getString(CATALOG_SEARCH_QUERY_PREF, "") ?: ""

    // ============================== Filters =====================================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header(SEARCH_FORMAT_MSG),
        Filter.Separator(),
        Filter.Header("Paste full URL into search bar to open gallery/art."),
    )

    companion object {
        private const val SEARCH_FORMAT_MSG = "Browse user's gallery by searching for `gallery:{username}` or `gallery:{username}/{folderId}`\n" +
            "Or search for an artist's individual arts with `by:{username}`"
        private const val DESCRIPTION_GUIDE = "Click tag to show artist's gallery"

        private const val CATALOG_FORMAT_MSG = "Show Popular with:"
        private const val CATALOG_SEARCH_QUERY = "search query of: "
        private const val CATALOG_SEARCH_QUERY_PREF = "catalog_search_query_pref"

        private const val CHAPTER_NAME_PREF = "includeArtIdAsChapterNamePref"

        private val GALLERY_QUERY_REGEX = Regex("""gallery:([\w-]+)(?:/(\d+))?""")
        private val ART_LINK_REGEX = Regex("""(.+/art/[^/?#]+)""")
        private val RCACHE_REGEX = Regex("""window\.__RCACHE__ = JSON\.parse\(("\{.+?\}")\);""")
        private val INITIAL_STATE_REGEX = Regex("""window\.__INITIAL_STATE__ = JSON\.parse\(("\{.+?\}")\);""")
    }
}

private fun DateTimeFormatter.tryParse(date: String): Long = try {
    ZonedDateTime.parse(date, this).toInstant().toEpochMilli()
} catch (_: DateTimeException) {
    0L
}
