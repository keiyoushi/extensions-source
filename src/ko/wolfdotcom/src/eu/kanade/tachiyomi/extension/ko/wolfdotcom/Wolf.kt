package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Wolf :
    KeiSource(),
    ConfigurableSource {

    // Source type is determined by the source name suffix.
    private val isWebtoon get() = name.endsWith("웹툰")
    private val isComic get() = name.endsWith("만화책")

    // Site path segments differ between webtoon and comic sources.
    private val browsePath get() = if (isComic) "cm" else "ing"
    private val entryPath get() = if (isComic) "cl" else "list"
    private val readerPath get() = if (isComic) "cv" else "view"

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = this

    // Telegram channel listing the current site address.
    private val preferences by getPreferencesLazy()
    private val telegramUrlPref by lazy {
        preferences.getString("telegram_url", DEFAULT_TELEGRAM_URL) ?: DEFAULT_TELEGRAM_URL
    }

    private val domainRegex = Regex("""https://wfwf\d+\.com""")

    @Volatile
    private var resolvedBaseUrl: String? = null

    // Fetch the Telegram channel once per run to find the current site domain,
    // falling back to the static baseUrl when the channel is unreachable.
    // If the resolved domain differs from the one stored in the extension
    // settings, update the "Custom base URL" preference so the settings screen
    // reflects the current address.
    private suspend fun currentBaseUrl(): String {
        resolvedBaseUrl?.let { return it }
        val resolved = runCatching {
            val document = client.get(telegramUrlPref.toHttpUrl()).asJsoup()
            document.select("a[href]").mapNotNull { el -> el.attr("abs:href") }
                .firstOrNull { domainRegex.containsMatchIn(it) }
                ?: domainRegex.find(document.text())?.value
        }.getOrNull()

        if (resolved != null && resolved != baseUrl) {
            preferences.edit().putString("overrideBaseUrl", resolved).apply()
        }

        return (resolved ?: baseUrl).also { resolvedBaseUrl = it }
    }

    // Popular = sort by "f" (인기순)
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", POPULAR)

    // Latest = sort by "n" (최신순)
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", LATEST)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            return querySearch(query)
        }

        val pageUrl = "${currentBaseUrl()}/$browsePath".toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlPartFilter>().forEach { filter ->
                filter.addToUrl(this)
            }
            addQueryParameter("pg", page.toString())
        }.build()

        val document = client.get(pageUrl).asJsoup()

        val entries = document.select("a.t-card")
            .filter { card -> card.absUrl("href").contains("toon=") }
            .map { card ->
                val id = card.absUrl("href").toHttpUrl().queryParameter("toon")!!
                SManga.create().apply {
                    url = "/$entryPath?toon=$id"
                    title = card.selectFirst(".t-title")!!.text()
                    thumbnail_url = portraitCover(card.selectFirst(".t-img img")?.absUrl("src"))
                }
            }

        val hasNext = entries.isNotEmpty() && hasNextPageLink(document)

        return MangasPage(entries, hasNext)
    }

    private fun hasNextPageLink(document: org.jsoup.nodes.Document): Boolean {
        // The next-page arrow is the last .pg-btn inside div.pagi.
        // On browse pages it has class "pg-btn arr"; on detail pages just "pg-btn".
        // When there is no next page it is a <span> with no href.
        val arrow = document.selectFirst("div.pagi > .pg-btn:last-child") ?: return false
        return arrow.tagName() == "a" && arrow.hasAttr("href")
    }

    // Strip non-Korean/alphanumeric characters from the search query.
    private val specialChars = Regex("""[^\p{InHangul_Syllables}0-9a-z ]""", RegexOption.IGNORE_CASE)

    // The site's covers are 2:1 landscape banners; use them as-is for the
    // best available resolution (mihon crops them with ContentScale.Crop).
    private fun portraitCover(url: String?): String? = url

    private suspend fun querySearch(query: String): MangasPage {
        if (query.length < 2) {
            throw Exception("두 글자 이상 입력 해주세요.")
        }
        val stdQuery = query.replace(specialChars, "")
        // The site uses EUC-KR charset, so the query must be EUC-KR encoded.
        val searchUrl = "${currentBaseUrl()}/sh?q=${URLEncoder.encode(stdQuery, "EUC-KR")}".toHttpUrl()

        val document = client.get(searchUrl).asJsoup()

        // Keep only cards that belong to this source type.
        val entries = document.select("a.t-card")
            .filter { card -> card.absUrl("href").contains("/$entryPath?toon=") }
            .map { card ->
                val id = card.absUrl("href").toHttpUrl().queryParameter("toon")!!
                SManga.create().apply {
                    url = "/$entryPath?toon=$id"
                    title = card.selectFirst(".t-title")!!.text()
                    thumbnail_url = portraitCover(card.selectFirst(".t-img img")?.absUrl("src"))
                }
            }

        return MangasPage(entries, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val document = client.get((currentBaseUrl() + manga.url).toHttpUrl()).asJsoup()

        return SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.w-title")!!.text()
            thumbnail_url = portraitCover(document.selectFirst(".thumb-wrap img")?.absUrl("src"))
            description = document.selectFirst(".summary")?.text()
            genre = document.select("a.gtag")
                .joinToString(", ") { it.text().removePrefix("#") }
                .ifEmpty { null }
            author = document.selectFirst(".w-author")?.text()
            initialized = true
        }
    }

    // Encoded chapter URL (toon + num) so the reader URL can be rebuilt.
    @Serializable
    class ChapterUrl(
        val toon: String,
        val num: String,
    )

    // Walk all pages of the detail page to collect the full chapter list.
    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val entries = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val pageUrl = (currentBaseUrl() + manga.url).toHttpUrl().newBuilder().apply {
                addQueryParameter("pg", page.toString())
            }.build()

            val document = client.get(pageUrl).asJsoup()
            val pageChapters = document.select("a.ep-item").map { el ->
                val chapUrl = el.absUrl("href").toHttpUrl()
                SChapter.create().apply {
                    url = ChapterUrl(
                        chapUrl.queryParameter("toon")!!,
                        chapUrl.queryParameter("num")!!,
                    ).toJsonString()
                    name = el.selectFirst(".ep-title")!!.text()
                    date_upload = dateFormat.tryParse(el.selectFirst(".ep-date")?.text())
                }
            }

            if (pageChapters.isEmpty()) break
            entries.addAll(pageChapters)

            if (!hasNextPageLink(document)) break
            page++
        }

        return entries
    }

    // Rebuild the reader URL from the encoded ChapterUrl JSON.
    override fun getChapterUrl(chapter: SChapter): String {
        val chapUrl = chapter.url.parseAs<ChapterUrl>()
        return "${resolvedBaseUrl ?: baseUrl}/$readerPath?toon=${chapUrl.toon}&num=${chapUrl.num}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter).toHttpUrl()).asJsoup()

        // Images use data-src (lazy loading) inside the viewer area.
        return document.select("#vimg-area img[data-src]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-src"))
        }
    }

    // Deeplink: resolve a site URL to an SManga.
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != currentBaseUrl().toHttpUrl().host) return null

        if (url.pathSegments.firstOrNull() != entryPath) return null
        val toon = url.queryParameter("toon") ?: return null

        val manga = SManga.create().apply { this.url = "/$entryPath?toon=$toon" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
        )

        if (isComic) {
            filters.add(ComicGenreFilter())
        } else {
            filters.add(TypeFilter())
            filters.add(DayFilter())
            filters.add(GenreFilter())
        }

        return FilterList(filters)
    }

    // Let the user override the Telegram channel that lists the current site
    // address. Falls back to the static baseUrl when it cannot be resolved.
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "telegram_url"
            title = "주소 공지 채널"
            summary = "현재 사이트 주소를 알려주는 텔레그램 채널 URL"
            setDefaultValue(DEFAULT_TELEGRAM_URL)
        }.also(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_TELEGRAM_URL = "https://t.me/s/wfwf_com"

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        }
    }
}
