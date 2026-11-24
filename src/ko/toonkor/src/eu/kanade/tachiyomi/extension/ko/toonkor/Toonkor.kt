package eu.kanade.tachiyomi.extension.ko.toonkor

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.MirrorPreferences
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toonkor : KeiSource() {
    // 툰코 and 펀비 move to a new domain every few days, and their old domains redirect to it
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = coroutineScope {
        (fetchableBaseUrl as MirrorPreferences).mirrors.map { (_, url) ->
            async { client.get(url, Fetch.DOCUMENT).use { it.request.url.toString() } }
        }.awaitAll()
    }

    // The site serves a reduced 30-item listing to Android user agents.
    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        val chromeVersion = CHROME_VERSION_REGEX.find(get("User-Agent").orEmpty())?.groupValues?.get(1) ?: "156"
        return set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion.0.0.0 Safari/537.36")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_POPULAR", Fetch.DOCUMENT))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_LATEST", Fetch.DOCUMENT))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()
        val status = filters.firstInstanceOrNull<StatusFilter>()
        val sort = filters.firstInstanceOrNull<SortFilter>()

        val requestPath = when {
            query.isNotEmpty() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            else -> "${type?.toUriPart() ?: ""}${status?.toUriPart() ?: ""}${sort?.toUriPart() ?: ""}"
        }

        return parseMangaList(client.get(baseUrl + requestPath, Fetch.DOCUMENT))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.section-item-inner").mapNotNull { element ->
            val link = element.selectFirst("div.section-item-title a") ?: return@mapNotNull null
            val title = link.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        with(document.select("table.bt_view1")) {
            select("td.bt_title").text().takeIf { it.isNotBlank() }?.let { title = it }
            // The info rows also give the number of episodes
            author = info("작가")
            genre = info("장르")?.split('/')?.joinToString { it.trim() }
            description = select("td.bt_over").text()
            selectFirst("td.bt_thumb img")?.absUrl("src")?.takeIf { it.isNotBlank() }?.let {
                thumbnail_url = it
            }
        }
        initialized = true
    }

    private fun Elements.info(label: String): String? = select("span.bt_label:containsOwn($label) + span.bt_data").textOrNull()

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table.web_list tr:has(td.content__title)").mapNotNull { element ->
        val titleEl = element.selectFirst("td.content__title") ?: return@mapNotNull null
        val url = titleEl.attr("data-role").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        // The cell also shows the comment count and the date
        val name = titleEl.selectFirst("a.tk-eplnk")?.attrOrNull("aria-label") ?: return@mapNotNull null

        SChapter.create().apply {
            this.url = url
            this.name = name
            date_upload = dateFormat.tryParseDate(element.selectFirst("td.episode__index")?.text(), KOREA_ZONE)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        // Several scripts mention toon_img; only the one assigning the base64 blob is useful,
        // so match the assignment instead of taking the first quoted string of the first match.
        val encoded = document.select("script")
            .firstNotNullOfOrNull { toonImgRegex.find(it.data())?.groupValues?.get(1) }
            ?: return emptyList()

        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))

        return pageListRegex.findAll(decoded).mapIndexed { i, matchResult ->
            val imageUrl = matchResult.destructured.component1().let { if (it.startsWith("http")) it else baseUrl + it }
            Page(i, imageUrl = imageUrl)
        }.toList()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!FRONT_HOST.matches(url.host)) return null
        // Decoded, as the lists give it
        val path = url.pathSegments.joinToString("/", prefix = "/")
        if (path == "/" || path.startsWith("/bbs/") || path.startsWith("/css/") || path.startsWith("/js/")) return null
        val manga = SManga.create().apply {
            // A chapter page links back to its series
            this.url = if (path.endsWith(".html")) {
                client.get(baseUrl.toHttpUrl().resolve(path)!!, Fetch.DOCUMENT).asJsoup().selectFirst("a.tkbn-list")!!.attr("href")
            } else {
                path
            }
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Note: can't combine with text search!"),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val KOREA_ZONE = ZoneId.of("Asia/Seoul")
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
        private val toonImgRegex = Regex("""toon_img\s*=\s*'([^']+)'""")
        private val pageListRegex = Regex("""src="([^"]*)"""")
        private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+)""")

        // Current and older domains of 툰코 and 펀비
        private val FRONT_HOST = Regex("""\d+\.toonkor\.net|toonkor\d+\.org|tkor\d+\.com|\d+\.funbe\.org|funbe\d+\.com""")
    }
}
