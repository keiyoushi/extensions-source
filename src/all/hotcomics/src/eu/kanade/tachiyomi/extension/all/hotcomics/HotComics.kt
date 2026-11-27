package eu.kanade.tachiyomi.extension.all.hotcomics

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
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class HotComics :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "de"),
        classLoader = this::class.java.classLoader!!,
    )

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::imageBackupInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/$lang/ranking", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/$lang/new", Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addEncodedPathSegments("$lang/search")
                addQueryParameter("keyword", query.trim())
            } else {
                val filter = filters.firstInstance<BrowseFilter>()
                addEncodedPathSegments(filter.selected)
            }
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val entries = document.select("li[itemtype*=ComicSeries]:not(.no-comic) > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("div.visual img")?.imgAttr()
                title = element.selectFirst("div.main-text > h4.title")!!.text()
            }
        }
        val hasNextPage = document.selectFirst("div.pagination a.vnext:not(.disabled)") != null

        return MangasPage(entries, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.encodedPathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 3 || segments[0] != lang || !segments[2].endsWith(".html")) return null
        val seriesSegments = when {
            segments[2].startsWith("episode-") ->
                client.get(baseUrl.toHttpUrl().resolve(url.encodedPath)!!, Fetch.DOCUMENT).asJsoup()
                    .selectFirst("a.vlist")!!.absUrl("href").toHttpUrl().encodedPathSegments
            else -> segments
        }
        // Episode pages link back to the series under /en whatever their language
        val manga = SManga.create().apply { this.url = "/$lang/${seriesSegments[1]}/${seriesSegments[2]}" }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return SMangaUpdate(
            mangaDetailsParse(manga, document),
            chapters = { if (fetchChapters) chapterListParse(document, chapters) else chapters },
        )
    }

    private fun mangaDetailsParse(manga: SManga, document: Document) = manga.apply {
        title = document.selectFirst("h2.episode-title")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
        author = document.selectFirst("p.type_box span.writer")?.text()?.removePrefix("Ⓒ")?.trim()
        genre = document.selectFirst("p.type_box span.type")?.text()
            ?.split("/")?.joinToString { it.trim() }
        val schedule = document.selectFirst("div.episode-contents header h2")?.text()
        status = when {
            schedule == null -> SManga.UNKNOWN
            "completed" in schedule || "abgeschlossen" in schedule -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        description = MarkdownDescription.Builder()
            .info(schedule)
            .description(document.selectFirst("#descText")?.text())
            .build()
    }

    private suspend fun chapterListParse(document: Document, stored: List<SChapter>): List<SChapter> {
        val seriesTitle = document.selectFirst("h2.episode-title")!!.text()
        val chapters = document.select("#tab-chapter a:has(.cell-num)").map { element ->
            SChapter.create().apply {
                // German series link their episodes under /en
                val episode = chapterUrlRegex.find(element.attr("onclick"))!!.groupValues[1].toHttpUrl().encodedPathSegments
                url = "/$lang/${episode[1]}/${episode[2]}"
                name = buildString {
                    append(element.selectFirst(".cell-mobile .cell-num")!!.text())
                    element.selectFirst(".cell-title strong")?.text()
                        ?.takeUnless { seriesTitle.startsWith(it) }
                        ?.let { append(" - ", it) }
                }
                date_upload = dateFormat.tryParseDate(element.selectFirst(".cell-time")?.text())
            }
        }.reversed()
        val lockedCount = lockedCount(chapters, stored)
        val showLocked = preferences.getBoolean(SHOW_LOCKED_PREF, false)

        return chapters.mapIndexedNotNull { index, chapter ->
            when {
                index >= lockedCount -> chapter.apply {
                    memo = buildJsonObject {
                        put(FREE_MEMO, true)
                        if (index == lockedCount) put(LOCKED_ABOVE_MEMO, lockedCount)
                    }
                }
                showLocked -> chapter.apply { name = "🔒 $name" }
                else -> null
            }
        }
    }

    // Locked episodes are the newest ones; probe from the end where the last refresh puts the first free one
    private suspend fun lockedCount(chapters: List<SChapter>, stored: List<SChapter>): Int {
        val storedMemos = stored.associate { it.url to it.memo }
        val lastFree = chapters.indexOfFirst { storedMemos[it.url]?.getBooleanOrNull(FREE_MEMO) == true }
        val unknown = if (lastFree == -1) chapters.size else lastFree
        val expected = chapters.getOrNull(lastFree)?.let { storedMemos[it.url]?.getIntOrNull(LOCKED_ABOVE_MEMO) } ?: 0

        return if (lastFree == -1 || expected < unknown - expected) {
            (0 until unknown).firstOrNull { !isLocked(chapters[it]) } ?: unknown
        } else {
            (unknown - 1 downTo 0).firstOrNull { isLocked(chapters[it]) }?.plus(1) ?: 0
        }
    }

    private suspend fun isLocked(chapter: SChapter) = fetchImageSources(chapter).isPlaceholder()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = fetchImageSources(chapter)
        if (images.isPlaceholder()) return emptyList()

        return images.mapIndexed { idx, sources ->
            val imageUrl = sources.first().toHttpUrl().newBuilder()
                .fragment(sources.drop(1).takeIf { it.isNotEmpty() }?.toJsonString())
                .build()
            Page(idx, imageUrl = imageUrl.toString())
        }
    }

    private suspend fun fetchImageSources(chapter: SChapter) = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#viewer-img img").map { it.attrOrNull("data-backup-sources")?.parseAs<List<String>>() ?: listOf(it.imgAttr()) }

    // Locked episodes show a single placeholder image from the site's own host
    private fun List<List<String>>.isPlaceholder() = singleOrNull()?.first()?.toHttpUrl()?.host == baseUrl.toHttpUrl().host

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/$lang/genres", Fetch.DOCUMENT).asJsoup()
        .select("li.genres-li a")
        .map { it.text() to it.absUrl("href").toHttpUrl().encodedPath.removePrefix("/") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val pages = listOf(
            intl["browse_ranking"] to "$lang/ranking",
            intl["browse_home"] to lang,
            intl["browse_weekly"] to "$lang/weekly",
            intl["browse_new"] to "$lang/new",
        )
        val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty().map { (name, path) -> intl.format("browse_genre", name) to path }

        return FilterList(
            Filter.Header(intl["browse_text_search"]),
            Filter.Separator(),
            BrowseFilter(intl["browse"], pages + genres),
        )
    }

    // Image hosts that refused a page, tried last for later pages
    private val refusedHosts = ConcurrentHashMap.newKeySet<String>()

    // The site reader's next host for an image that fails to load
    private fun imageBackupInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Page lists cached by 1.6.4 prefix the backups with "fallback"
        val backups = request.url.fragment?.removePrefix("fallback")?.takeIf { it.startsWith("[") }?.parseAs<List<String>>()
            ?: return chain.proceed(request)

        val urls = (listOf(request.url) + backups.map { it.toHttpUrl() }).sortedBy { it.host in refusedHosts }
        for (url in urls.dropLast(1)) {
            val response = try {
                chain.proceed(request.newBuilder().url(url).build())
            } catch (_: IOException) {
                null
            }
            if (response?.isSuccessful == true) return response
            if (response != null && response.code < 500) refusedHosts += url.host
            response?.close()
        }
        return chain.proceed(request.newBuilder().url(urls.last()).build())
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = intl["show_locked_title"]
            summary = intl["show_locked_summary"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }
}

private const val SHOW_LOCKED_PREF = "show_locked_episodes"
private const val FREE_MEMO = "free"
private const val LOCKED_ABOVE_MEMO = "lockedAbove"

private val chapterUrlRegex = Regex("""popupLogin\('([^']+)'""")

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
