package eu.kanade.tachiyomi.extension.en.rinkocomics

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RinkoComics :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.encodedPath == AJAX_PATH }

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "updated")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = comicsUrl(page)

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sort", filter.value)
                is StatusFilter -> filter.value.takeIf { it.isNotEmpty() }?.let { url.addQueryParameter("status", it) }
                is GenreFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("genres[]", it.slug) }
                else -> {}
            }
        }

        return parseComicsPage(client.get(url.build(), Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val mangaUrl = when (url.pathSegments[0]) {
            "comic" -> "$baseUrl/comic/$slug/"
            "chapter" -> client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("main a[href*=/comic/]")!!.attr("abs:href")
            else -> return null
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaUrl) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        val updatedManga = parseMangaDetails(document).apply { url = manga.url }

        if (!fetchChapters) return SMangaUpdate(updatedManga, chapters)

        return SMangaUpdate(updatedManga, parseChapterList(document, mangaUrl))
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("main h1")!!.text()
        thumbnail_url = document.selectFirst("main aside img")?.largestImage()
        status = parseStatus(document.info("Status"))
        genre = buildList {
            document.info("Type")?.let(::add)
            document.select("main a[href*=/comics_genres/]").mapTo(this) { it.text() }
        }.joinToString()

        val altTitles = document.selectFirst("main p:has(> span:containsOwn(Also known as))")
            ?.ownText()
            ?.split(" · ")
            .orEmpty()
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#cwSynopsis"))
            .altTitles(altTitles)
            .build()
    }

    private suspend fun parseChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED, true)
        val firstPage = parseChapterElements(document.select("#cwChapters a.chapter-item"), hideLocked)

        val moreButton = document.selectFirst("#cwMoreChapters") ?: return firstPage
        val comicId = moreButton.attr("data-comic-id")
        val pageSize = moreButton.attr("data-offset").toInt()
        val total = document.info("Chapters")!!.toInt()
        val nonce = NONCE_REGEX.find(document.selectFirst("script#comicworld-loadmore-js-extra")!!.data())!!.groupValues[1]

        val morePages = coroutineScope {
            (pageSize until total step pageSize).map { offset ->
                async { fetchMoreChapters(mangaUrl, comicId, offset, nonce, hideLocked) }
            }.awaitAll()
        }

        return firstPage + morePages.flatten()
    }

    private suspend fun fetchMoreChapters(
        mangaUrl: String,
        comicId: String,
        offset: Int,
        nonce: String,
        hideLocked: Boolean,
    ): List<SChapter> {
        val body = FormBody.Builder()
            .add("action", "load_more_chapters")
            .add("nonce", nonce)
            .add("comic_id", comicId)
            .add("offset", offset.toString())
            .add("order", "desc")
            .add("ui", "v2")
            .build()

        val html = client.post(baseUrl.toHttpUrl().newBuilder().encodedPath(AJAX_PATH).build(), Fetch.XHR.from(mangaUrl), body)
            .parseAs<AjaxResponse>()
            .data.html
        return parseChapterElements(Jsoup.parseBodyFragment(html, baseUrl).select("a.chapter-item"), hideLocked)
    }

    private fun parseChapterElements(elements: List<Element>, hideLocked: Boolean): List<SChapter> = elements.mapNotNull { element ->
        val locked = element.hasAttr("data-reason")
        if (locked && hideLocked) return@mapNotNull null

        SChapter.create().apply {
            setUrlWithoutDomain(if (locked) element.attr("abs:data-permalink") else element.attr("abs:href"))
            val chapterName = element.selectFirst("> span > span:first-child")!!.text()
            name = if (locked) "$LOCK_PREFIX$chapterName" else chapterName
            date_upload = dateFormat.tryParseDate(
                element.selectFirst("> span > span:nth-child(2)")?.text()?.substringBefore(" · "),
            )
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val payload = document.selectFirst("script#cwPages")?.data()
            ?: return emptyList()

        return decodePages(payload.trim()).mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(comicsUrl(1).build(), Fetch.DOCUMENT).asJsoup()

        return document.select("#cwBrowse input[name='genres[]']")
            .map { input -> input.nextElementSibling()!!.text() to input.attr("value") }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(SortFilter(), StatusFilter())

        data?.parseAs<List<Pair<String, String>>>()?.also { genres ->
            filters += GenreFilter(genres.map { Genre(it.first, it.second) })
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED
            title = "Hide paid chapters"
            summary = "Hide locked/paid chapters from the list."
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private suspend fun browse(page: Int, sort: String): MangasPage {
        val url = comicsUrl(page).addQueryParameter("sort", sort).build()
        return parseComicsPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun comicsUrl(page: Int): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("comic")
        if (page > 1) addPathSegments("page/$page")
        addPathSegment("")
    }

    private fun parseComicsPage(document: Document): MangasPage {
        val entries = document.select("#cwBrowse ul > li > a").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.attr("abs:href"))
                title = card.selectFirst("> span:nth-of-type(2)")!!.text()
                thumbnail_url = card.selectFirst("img")?.largestImage()
            }
        }

        val hasNextPage = document.selectFirst("nav[aria-label=Pages] a.next") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun Element.largestImage(): String? = attrOrNull("srcset")
        ?.split(", ")
        ?.maxBy { it.substringAfterLast(' ').removeSuffix("w").toIntOrNull() ?: 0 }
        ?.substringBefore(' ')

    private fun Document.info(label: String): String? = selectFirst("main dt:containsOwn($label) + dd")?.textOrNull()

    private fun decodePages(payload: String): List<String> {
        val raw = Base64.decode(payload, Base64.DEFAULT)

        return ByteArray(raw.size - 16) { i ->
            ((raw[i + 16].toInt() and 0xFF) xor (raw[i % 16].toInt() and 0xFF)).toByte()
        }.decodeToString().parseAs()
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    @Serializable
    private class AjaxResponse(val data: AjaxData)

    @Serializable
    private class AjaxData(val html: String)

    companion object {
        private const val PREF_HIDE_LOCKED = "hide_paid_chapters"
        private const val LOCK_PREFIX = "🔒 "
        private const val AJAX_PATH = "/wp-admin/admin-ajax.php"
        private val NONCE_REGEX = Regex(""""nonce":"([^"]+)"""")
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
