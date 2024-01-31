package eu.kanade.tachiyomi.extension.en.manhuascan

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

class ManhuaScan : ConfigurableSource, ParsedHttpSource() {

    override val lang = "en"

    override val supportsLatest = true

    override val name = "ManhuaScan"

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val baseUrl = getMirror()

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/popular${page.getPage()}", headers)

    override fun popularMangaSelector(): String = ".manga-list > .book-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst(".thumb img")?.imgAttr()
        element.selectFirst(".title a")!!.run {
            title = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    override fun popularMangaNextPageSelector(): String = ".paginator > .active + a"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest${page.getPage()}", headers)

    override fun latestUpdatesSelector(): String =
        popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        popularMangaNextPageSelector()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = filters.firstInstanceOrNull<GenreFilter>()
        val genreInclusion = filters.firstInstanceOrNull<GenreInclusionFilter>()
        val status = filters.firstInstanceOrNull<StatusFilter>()
        val orderBy = filters.firstInstanceOrNull<OrderByFilter>()
        val author = filters.firstInstanceOrNull<AuthorFilter>()

        val url = "$baseUrl/search${page.getPage()}".toHttpUrl().newBuilder().apply {
            genre?.included?.forEach {
                addEncodedQueryParameter("include[]", it)
            }
            genre?.excluded?.forEach {
                addEncodedQueryParameter("exclude[]", it)
            }
            addQueryParameter("include_mode", genreInclusion?.toUriPart())
            addQueryParameter("bookmark", "off")
            addQueryParameter("status", status?.toUriPart())
            addQueryParameter("sort", orderBy?.toUriPart())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            if (author?.state?.isNotEmpty() == true) {
                addQueryParameter("author", author.state)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String =
        popularMangaNextPageSelector()

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        GenreInclusionFilter(),
        Filter.Separator(),
        StatusFilter(),
        OrderByFilter(),
        AuthorFilter(),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        var alternativeName = ""

        document.selectFirst(".book-info")?.run {
            genre = select(".meta p:has(strong:contains(Genres)) a").joinToString(", ") { it.text().removeSuffix(" ,") }
            author = select(".meta p:has(strong:contains(Authors)) a").joinToString(", ") { it.text() }
            thumbnail_url = selectFirst("#cover img")?.imgAttr()
            status = selectFirst(".meta p:has(strong:contains(Status)) a").parseStatus()
            title = selectFirst("h1")!!.text()
            selectFirst("h2")?.also {
                alternativeName = it.text()
            }
        }

        description = buildString {
            document.selectFirst(".summary > p:not([style]):not(:empty)")?.let {
                append(it.text())
                if (alternativeName.isNotEmpty()) {
                    append("\n\n")
                }
            }
            if (alternativeName.isNotEmpty()) {
                append("Alternative name(s): $alternativeName")
            }
        }
    }

    private fun Element?.parseStatus(): Int = with(this?.text()) {
        return when {
            equals("ongoing", true) -> SManga.ONGOING
            equals("completed", true) -> SManga.COMPLETED
            equals("on-hold", true) -> SManga.ON_HIATUS
            equals("canceled", true) -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("manga/").substringBefore("-")

        val chapterHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", baseUrl.toHttpUrl().host)
            set("Referer", baseUrl + manga.url)
        }.build()

        val url = "$baseUrl/service/backend/chaplist/?manga_id=$id&manga_name=${manga.title}"

        return GET(url, chapterHeaders)
    }

    override fun chapterListSelector() = "ul > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("time")?.also {
            date_upload = it.text().parseRelativeDate()
        }
        name = element.selectFirst("strong")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
    }

    // From OppaiStream
    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var parsedDate = 0L
        val relativeDate = this.split(" ").firstOrNull()
            ?.replace("one", "1")
            ?.replace("a", "1")
            ?.toIntOrNull()
            ?: return 0L

        when {
            // parse: 30 seconds ago
            "second" in this -> {
                parsedDate = now.apply { add(Calendar.SECOND, -relativeDate) }.timeInMillis
            }
            // parses: "42 minutes ago"
            "minute" in this -> {
                parsedDate = now.apply { add(Calendar.MINUTE, -relativeDate) }.timeInMillis
            }
            // parses: "1 hour ago" and "2 hours ago"
            "hour" in this -> {
                parsedDate = now.apply { add(Calendar.HOUR, -relativeDate) }.timeInMillis
            }
            // parses: "2 days ago"
            "day" in this -> {
                parsedDate = now.apply { add(Calendar.DAY_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 weeks ago"
            "week" in this -> {
                parsedDate = now.apply { add(Calendar.WEEK_OF_YEAR, -relativeDate) }.timeInMillis
            }
            // parses: "2 months ago"
            "month" in this -> {
                parsedDate = now.apply { add(Calendar.MONTH, -relativeDate) }.timeInMillis
            }
            // parse: "2 years ago"
            "year" in this -> {
                parsedDate = now.apply { add(Calendar.YEAR, -relativeDate) }.timeInMillis
            }
        }
        return parsedDate
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        val scriptData = document.selectFirst("script:containsData(chapterId)")?.data()
            ?: throw Exception("Unable to find script data")
        val chapterId = CHAPTER_ID_REGEX.find(scriptData)?.groupValues?.get(1)
            ?: throw Exception("Unable to retrieve chapterId")

        val pagesHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", baseUrl.toHttpUrl().host)
            set("Referer", document.location())
        }.build()
        val pagesUrl = "$baseUrl/service/backend/chapterServer/?server_id=$server&chapter_id=$chapterId"

        val pagesDocument = client.newCall(
            GET(pagesUrl, pagesHeaders),
        ).execute().asJsoup()

        return pagesDocument.select("div").map { page ->
            val url = page.imgAttr()
            val index = page.id().substringAfterLast("-").toInt()
            Page(index, document.location(), url)
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
            set("Referer", page.url)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private fun Int.getPage(): String = if (this == 1) "" else "?page=$this"

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    companion object {
        private val CHAPTER_ID_REGEX = Regex("""chapterId\s*=\s*(\d+)""")

        private const val MIRROR_PREF_KEY = "pref_mirror"
        private const val MIRROR_PREF_TITLE = "Select Mirror (Requires Restart)"
        private val MIRROR_PREF_ENTRIES = arrayOf("manhuascan.com", "manhuascan.io", "mangajinx.com")
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES.map { "https://$it" }.toTypedArray()
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES.first()

        private const val SERVER_PREF_KEY = "pref_server"
        private const val SERVER_PREF_TITLE = "Image Server"
        private val SERVER_PREF_ENTRIES = arrayOf("Server 1", "Server 2")
        private val SERVER_PREF_ENTRY_VALUES = SERVER_PREF_ENTRIES.map { it.substringAfter(" ") }.toTypedArray()
        private val SERVER_PREF_DEFAULT_VALUE = SERVER_PREF_ENTRY_VALUES.first()
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SERVER_PREF_KEY
            title = SERVER_PREF_TITLE
            entries = SERVER_PREF_ENTRIES
            entryValues = SERVER_PREF_ENTRY_VALUES
            setDefaultValue(SERVER_PREF_DEFAULT_VALUE)
            summary = "%s"
        }.also(screen::addPreference)
    }

    private fun getMirror(): String =
        preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)!!

    private val server
        get() = preferences.getString(SERVER_PREF_KEY, SERVER_PREF_DEFAULT_VALUE)!!
}
