package eu.kanade.tachiyomi.extension.all.webtoons

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.Calendar

open class Webtoons(
    override val lang: String,
    private val langCode: String = lang,
    localeForCookie: String = lang,
    private val dateFormat: SimpleDateFormat,
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
            val (_, titleLang, titleNo) = query.split(":", limit = 3)
            val tmpManga = SManga.create().apply {
                url = "/episodeList?titleNo=$titleNo"
            }
            return if (titleLang == langCode) {
                fetchMangaDetails(tmpManga).map {
                    MangasPage(listOf(it), false)
                }
            } else {
                Observable.just(
                    MangasPage(emptyList(), false),
                )
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
                    contains("END") || contains("TERMINÉ") -> SManga.COMPLETED
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

    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }

    override fun chapterListRequest(manga: SManga) = GET(mobileUrl + manga.url, mobileHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul#_episodeList li[id*=episode] a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".sub_title > span.ellipsis")!!.text()
                element.selectFirst("a > div.row > div.num")?.let {
                    name += " Ch. " + it.text().substringAfter("#")
                }
                element.selectFirst(".ico_bgm")?.also {
                    name += " ♫"
                }
                date_upload = dateFormat.tryParse(element.selectFirst(".sub_info .date")?.text())
            }
        }
    }

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
