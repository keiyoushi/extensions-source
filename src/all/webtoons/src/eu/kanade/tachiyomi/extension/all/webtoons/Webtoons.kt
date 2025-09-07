package eu.kanade.tachiyomi.extension.all.webtoons

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import rx.Observable
import java.net.SocketException
import java.text.DecimalFormat
import java.util.Calendar

open class Webtoons(
    override val lang: String,
    private val langCode: String = lang,
    localeForCookie: String = lang,
) : HttpSource(), ConfigurableSource {
    override val name = "Webtoons.com"
    override val baseUrl = "https://www.webtoons.com"
    private val mobileUrl = "https://m.webtoons.com"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val mobileHeaders = super.headersBuilder()
        .set("Referer", "$mobileUrl/")
        .build()

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(
            CookieInterceptor(
                domain = "webtoons.com",
                cookies = listOf(
                    "ageGatePass" to "true",
                    "locale" to localeForCookie,
                    "needGDPR" to "false",
                ),
            ),
        )
        .addInterceptor { chain ->
            // m.webtoons.com throws an SSL error that can be solved by a simple retry
            try {
                chain.proceed(chain.request())
            } catch (e: SocketException) {
                chain.proceed(chain.request())
            }
        }
        .addInterceptor(TextInterceptor())
        .rateLimitHost(mobileUrl.toHttpUrl(), 1)
        .build()

    private val preferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int): Request {
        val ranking = when (page) {
            1 -> "trending"
            2 -> "popular"
            3 -> "originals"
            4 -> "canvas"
            else -> throw Exception("page > 4 not available")
        }

        return GET("$baseUrl/$langCode/ranking/$ranking", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select(".webtoon_list li a")
            .map(::mangaFromElement)
        val hasNextPage = response.request.url.pathSegments.last() != "canvas"

        return MangasPage(entries, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            title = element.selectFirst(".title")!!.text()
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
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

        return GET("$baseUrl/$langCode/originals/$day?sortOrder=UPDATE", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select(".webtoon_list li a")
            .map(::mangaFromElement)

        return MangasPage(entries, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(ID_SEARCH_PREFIX)) {
            val (_, type, lang, titleNo) = query.split(":", limit = 4)
            val tmpManga = SManga.create().apply {
                url = buildString {
                    if (type == "canvas") {
                        append("/challenge")
                    }
                    append("/episodeList?titleNo=")
                    append(titleNo)
                }
            }

            return if (lang == langCode) {
                fetchMangaDetails(tmpManga).map {
                    MangasPage(listOf(it), false)
                }
            } else {
                Observable.just(MangasPage(emptyList(), false))
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            SearchType(),
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select(".webtoon_list li a").map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a.pagination[aria-current=true] + a") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { mangaDetailsParse(it, manga) }
    }

    private fun mangaDetailsParse(response: Response, oldManga: SManga): SManga {
        val document = response.asJsoup()

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
            initialized = true
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

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }

    override fun chapterListRequest(manga: SManga): Request {
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
        }.build()

        return GET(url, mobileHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
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

        if (unrecognized > recognized) {
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
        """(?:(s(eason)?|part|vol(ume)?)\s*\.?\s*(\d+).*?)?(.*?(mini|bonus|special).*?)?(e(p(isode)?)?|ch(apter)?)\s*\.?\s*(\d+(\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
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
                val creator = document.select("div.creator_note a.author_name span").text().trim()

                pages += Page(
                    pages.size,
                    imageUrl = TextInterceptorHelper.createUrl("Author's Notes from $creator", note),
                )
            }
        }

        return pages
    }

    private fun fetchMotionToonPages(document: Document): List<Page> {
        val docString = document.toString()

        val docUrlRegex = Regex("documentURL:.*?'(.*?)'")
        val motionToonPathRegex = Regex("jpg:.*?'(.*?)\\{")

        val docUrl = docUrlRegex.find(docString)!!.groupValues[1]
        val motionToonPath = motionToonPathRegex.find(docString)!!.groupValues[1]
        val motionToonResponse = client.newCall(GET(docUrl, headers)).execute()
        val motionToonImages = motionToonResponse.parseAs<MotionToonResponse>().assets.images

        return motionToonImages.entries
            .filter { it.key.contains("layer") }
            .mapIndexed { i, entry ->
                Page(i, imageUrl = motionToonPath + entry.value)
            }
    }

    private fun showAuthorsNotesPref() = preferences.getBoolean(SHOW_AUTHORS_NOTES_KEY, false)
    private fun useMaxQualityPref() = preferences.getBoolean(USE_MAX_QUALITY_KEY, false)

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
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}

private const val SHOW_AUTHORS_NOTES_KEY = "showAuthorsNotes"
private const val USE_MAX_QUALITY_KEY = "useMaxQuality"
const val ID_SEARCH_PREFIX = "id:"
