package eu.kanade.tachiyomi.extension.all.webtoons

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.textinterceptor.TextInterceptor
import keiyoushi.lib.textinterceptor.TextInterceptorHelper
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.SocketException
import java.text.DecimalFormat
import java.util.Calendar

@Source
abstract class Webtoons :
    KeiSource(),
    ConfigurableSource {
    // Due to lang code getting more specific for zh-Hant
    private val langCode: String get() = if (lang == "zh-Hant") "zh-hant" else lang
    private val localeForCookie: String get() = if (lang == "zh-Hant") "zh_TW" else lang

    private val mobileUrlHost by lazy { mobileUrl.toHttpUrl().host }

    private val mobileUrl = "https://m.webtoons.com"

    // headersBuilder() sets Origin to the desktop baseUrl; the mobile API is a different host
    // and has never been sent one.
    private val mobileHeaders by lazy {
        headersBuilder()
            .set("Referer", "$mobileUrl/")
            .removeAll("Origin")
            .build()
    }

    // 1.4's HttpSource defaulted this on; KeiSource defaults it off.
    override val supportRelatedMangasBySearch = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie(
            domain = { "webtoons.com" },
            cookies = {
                listOf(
                    "ageGatePass" to "true",
                    "locale" to localeForCookie,
                    "needGDPR" to "false",
                )
            },
        )
        addInterceptor { chain ->
            // m.webtoons.com throws an SSL error that can be solved by a simple retry
            try {
                chain.proceed(chain.request())
            } catch (e: SocketException) {
                chain.proceed(chain.request())
            }
        }
        addInterceptor(TextInterceptor())
        rateLimit(1) { it.host == mobileUrlHost }
    }

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val ranking = when (page) {
            1 -> "trending"
            2 -> "popular"
            3 -> "originals"
            4 -> "canvas"
            else -> throw Exception("page > 4 not available")
        }

        val document = client.get("$baseUrl/$langCode/ranking/$ranking").asJsoup()
        val entries = document.select(".webtoon_list li a")
            .map(::mangaFromElement)

        return MangasPage(entries, hasNextPage = ranking != "canvas")
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val day = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            Calendar.SATURDAY -> "saturday"
            Calendar.SUNDAY -> "sunday"
            else -> throw Exception("Unknown day of week")
        }

        val document = client.get("$baseUrl/$langCode/originals/$day?sortOrder=UPDATE").asJsoup()
        val entries = document.select(".webtoon_list li a")
            .map(::mangaFromElement)

        return MangasPage(entries, hasNextPage = false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SearchType(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.startsWith(ID_SEARCH_PREFIX)) {
            return searchById(query.removePrefix(ID_SEARCH_PREFIX))
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            var searchTypeAdded = false
            addPathSegment(langCode)
            addPathSegment("search")
            filters.firstInstanceOrNull<SearchType>()?.selected?.also {
                searchTypeAdded = true
                addPathSegment(it)
            }
            addQueryParameter("keyword", query)
            if (page > 1 && searchTypeAdded) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        val entries = document.select(".webtoon_list li a").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a.pagination[aria-current=true] + a") != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val titleNo = url.queryParameter("title_no")?.takeIf(::isTitleNo) ?: return null
        val path = url.pathSegments
        if (path.size < 3) return null

        // Every language ships as its own source; only the matching one resolves the link.
        if (path[0] != langCode) return null

        return resolve(mangaFor(path[1], titleNo))
    }

    /**
     * "id:<type>:<lang>:<titleNo>", kept from 1.4 so anything already relying on it still
     * resolves. 1.4 threw on a malformed one; this returns no results instead.
     */
    private suspend fun searchById(rest: String): MangasPage {
        val parts = rest.split(":")
        if (parts.size != 3) return MangasPage(emptyList(), false)
        val (type, lang, titleNo) = parts
        if (lang != langCode || !isTitleNo(titleNo)) return MangasPage(emptyList(), false)

        return MangasPage(listOf(resolve(mangaFor(type, titleNo))), false)
    }

    private fun isTitleNo(value: String) = value.isNotEmpty() && value.all(Char::isDigit)

    private fun mangaFor(type: String, titleNo: String) = SManga.create().apply {
        url = buildString {
            if (type == "canvas") {
                append("/challenge")
            }
            append("/episodeList?titleNo=")
            append(titleNo)
        }
    }

    private suspend fun resolve(manga: SManga) = getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // details and the episode list are on different hosts
        val detailsAsync = async {
            if (fetchDetails) {
                parseMangaDetails(client.get(getMangaUrl(manga)).asJsoup(), manga)
            } else {
                manga
            }
        }
        val chaptersAsync = async {
            if (fetchChapters) fetchChapterList(manga) else chapters
        }

        SMangaUpdate(manga = detailsAsync.await(), chapters = chaptersAsync.await())
    }

    private fun parseMangaDetails(document: Document, oldManga: SManga): SManga {
        val detailElement = document.selectFirst(".detail_header .info")
        val infoElement = document.selectFirst("#_asideDetail")

        return SManga.create().apply {
            setUrlWithoutDomain(document.location())
            title = document.selectFirst("h1.subj, h3.subj")!!.text()
            author = detailElement?.selectFirst(".author:nth-of-type(1)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText()
            artist = detailElement?.selectFirst(".author:nth-of-type(2)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText() ?: author
            genre = detailElement?.select(".genre").orEmpty().joinToString { it.text() }
            description = infoElement?.selectFirst("p.summary")?.text()
            status = with(infoElement?.selectFirst("p.day_info")?.text().orEmpty()) {
                when {
                    contains("UP") || contains("EVERY") || contains("NOUVEAU") -> SManga.ONGOING
                    contains("END") || contains("COMPLETED") || contains("TERMINÉ") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = run {
                val bannerFile = document.selectFirst(".detail_header .thmb img")
                    ?.absUrl("src")
                    ?.toHttpUrl()
                    ?.pathSegments
                    ?.lastOrNull()
                val oldThumbFile = oldManga.thumbnail_url
                    ?.toHttpUrl()
                    ?.pathSegments
                    ?.lastOrNull()
                val thumbnail = document.selectFirst("head meta[property=\"og:image\"]")
                    ?.attr("content")

                // replace banner image for toons in library
                if (oldThumbFile != null && oldThumbFile != bannerFile) {
                    oldManga.thumbnail_url
                } else {
                    thumbnail
                }
            }
        }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val webtoonUrl = getMangaUrl(manga).toHttpUrl()
        val titleId = webtoonUrl.queryParameter("title_no")
            ?: webtoonUrl.queryParameter("titleNo")
            ?: throw Exception("Migrate from $name to $name")

        val type = run {
            val path = webtoonUrl.pathSegments.filter(String::isNotEmpty)

            // older url pattern, people have in their library
            if (webtoonUrl.encodedPath.contains("episodeList")) {
                when (path[0]) {
                    // "/episodeList?titleNo=1049"
                    "episodeList" -> "webtoon"

                    // "/challenge/episodeList?titleNo=304446"
                    "challenge" -> "canvas"

                    else -> throw Exception("Migrate from $name to $name")
                }
            } else {
                // "/en/canvas/meme-girls/list?title_no=304446"
                if (path[1] == "canvas") {
                    "canvas"
                } else {
                    "webtoon"
                }
            }
        }

        val url = mobileUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("api/v1")
            addPathSegment(type)
            addPathSegment(titleId)
            addPathSegment("episodes")
            addQueryParameter("pageSize", "99999")
            if (type == "canvas") {
                addQueryParameter("readingLanguageCode", langCode)
            }
        }.build()

        return parseChapterList(client.get(url, mobileHeaders))
    }

    private fun parseChapterList(response: Response): List<SChapter> {
        val result = response.parseAs<EpisodeListResponse>()

        var recognized = 0
        var unrecognized = 0

        val chapters = result.result.episodeList.onEach { episode ->
            val match = episodeNoRegex
                .find(episode.episodeTitle)
                ?.groupValues
                ?.takeIf { it[6].isEmpty() } // skip mini/bonus episodes

            episode.chapterNumber = match?.get(11)?.toFloat() ?: -1f
            episode.seasonNumber = match?.get(4)?.takeIf(String::isNotBlank)?.toInt() ?: 1

            if (episode.chapterNumber == -1f) {
                unrecognized++
            } else {
                recognized++
            }
        }

        if (useSequentialNumberingPref() || unrecognized > recognized) {
            chapters.onEachIndexed { index, chapter ->
                chapter.chapterNumber = (index + 1).toFloat()
            }
        } else {
            var maxChapterNumber = 0f
            var currentSeason = 1
            var seasonOffset = 0f

            chapters.forEachIndexed { idx, chapter ->
                if (chapter.chapterNumber != -1f) {
                    val originalNumber = chapter.chapterNumber

                    // Check if we've moved to a new season
                    if (chapter.seasonNumber > currentSeason) {
                        currentSeason = chapter.seasonNumber
                        if (originalNumber <= maxChapterNumber) {
                            seasonOffset = maxChapterNumber
                        }
                    }

                    chapter.chapterNumber = seasonOffset + originalNumber
                    maxChapterNumber = maxOf(maxChapterNumber, chapter.chapterNumber)
                } else {
                    val previous = chapters.getOrNull(idx - 1)
                    if (previous == null) {
                        chapter.chapterNumber = 0f
                    } else {
                        chapter.chapterNumber = previous.chapterNumber + 0.01f
                    }
                }
            }
        }

        val numberFormatter = DecimalFormat("#.##")
        return chapters.map { episode ->
            SChapter.create().apply {
                url = episode.viewerLink
                name = buildString {
                    append(Parser.unescapeEntities(episode.episodeTitle, false))
                    append(" (ch. ", numberFormatter.format(episode.chapterNumber), ")")
                    if (episode.hasBgm) {
                        append(" ♫")
                    }
                }
                date_upload = episode.exposureDateMillis
                chapter_number = episode.chapterNumber
            }
        }.asReversed()
    }

    // season number - 4 capture group
    // possible bonus/mini/special episode - 6 capture group
    // episode number - 11 capture group
    private val episodeNoRegex = Regex(
        """(?:(s(eason)?|saison|part|vol(ume)?)\s*\.?\s*(\d+).*?)?(.*?(mini|bonus|special).*?)?(e(p(isode)?)?|ch(apter)?)\s*\.?\s*(\d+(\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val useMaxQuality = useMaxQualityPref()

        val pages = document.select("div#_imageList > img").mapIndexed { i, element ->
            val imageUrl = element.attr("data-url").toHttpUrl()

            if (useMaxQuality && imageUrl.queryParameter("type") == "q90") {
                val newImageUrl = imageUrl.newBuilder().apply {
                    removeAllQueryParameters("type")
                }.build()
                Page(i, imageUrl = newImageUrl.toString())
            } else {
                Page(i, imageUrl = imageUrl.toString())
            }
        }.toMutableList()

        if (pages.isEmpty()) {
            pages.addAll(
                fetchMotionToonPages(document),
            )
        }

        if (showAuthorsNotesPref()) {
            val note = document.select("div.creator_note p.author_text").text()

            if (note.isNotEmpty()) {
                val creator = document.select("div.creator_note .author_name span").text().trim()

                pages += Page(
                    pages.size,
                    imageUrl = TextInterceptorHelper.createUrl("Author's Notes from $creator", note),
                )
            }
        }

        return pages
    }

    private suspend fun fetchMotionToonPages(document: Document): List<Page> {
        val docString = document.toString()

        val docUrlRegex = Regex("documentURL:.*?'(.*?)'")
        val motionToonPathRegex = Regex("jpg:.*?'(.*?)\\{")

        val docUrl = docUrlRegex.find(docString)!!.groupValues[1]
        val motionToonPath = motionToonPathRegex.find(docString)!!.groupValues[1]
        val motionToonImages = client.get(docUrl).parseAs<MotionToonResponse>().assets.images

        return motionToonImages.entries
            .filter { it.key.contains("layer") }
            .mapIndexed { i, entry ->
                Page(i, imageUrl = motionToonPath + entry.value)
            }
    }

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)
    private fun useMaxQualityPref() = preferences.getBoolean(USE_MAX_QUALITY_KEY, false)
    private fun useSequentialNumberingPref() = preferences.getBoolean(USE_SEQUENTIAL_NUMBERING_KEY, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_AUTHORS_NOTES_KEY
            title = "Show author's notes"
            summary = "Enable to see the author's notes at the end of chapters (if they're there)."
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_MAX_QUALITY_KEY
            title = "Use maximum quality images"
            summary = "Enable to load images in maximum quality."
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = USE_SEQUENTIAL_NUMBERING_KEY
            title = "Use sequential chapter numbering"
            summary = "Enable to use sequential numbering instead of official episode numbers."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }
}

private const val ID_SEARCH_PREFIX = "id:"

private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
private const val USE_MAX_QUALITY_KEY = "useMaxQuality"
private const val USE_SEQUENTIAL_NUMBERING_KEY = "useSequentialNumbering"
