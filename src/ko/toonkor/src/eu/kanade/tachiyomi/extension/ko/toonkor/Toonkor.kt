package eu.kanade.tachiyomi.extension.ko.toonkor

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toonkor : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_POPULAR"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_LATEST"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstanceOrNull<TypeFilter>()
        val status = filters.firstInstanceOrNull<StatusFilter>()
        val sort = filters.firstInstanceOrNull<SortFilter>()

        val requestPath = when {
            query.isNotEmpty() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            else -> "${type?.toUriPart() ?: ""}${status?.toUriPart() ?: ""}${sort?.toUriPart() ?: ""}"
        }

        return parseMangaList(client.get(baseUrl + requestPath))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.section-item-inner").mapNotNull { element ->
            val link = element.selectFirst("div.section-item-title a") ?: return@mapNotNull null
            val title = link.selectFirst("h3")?.text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(link.attr("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = manga.apply {
        with(document.select("table.bt_view1")) {
            select("td.bt_title").text().takeIf { it.isNotBlank() }?.let { title = it }
            author = select("td.bt_label span.bt_data").text()
            description = select("td.bt_over").text()
            select("td.bt_thumb img").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() }?.let {
                thumbnail_url = it
            }
        }
        initialized = true
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table.web_list tr:has(td.content__title)").mapNotNull { element ->
        val titleEl = element.selectFirst("td.content__title") ?: return@mapNotNull null
        val url = titleEl.attr("data-role").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = titleEl.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null

        SChapter.create().apply {
            this.url = url
            this.name = name
            date_upload = dateFormat.tryParseDate(element.selectFirst("td.episode__index")?.text(), KOREA_ZONE)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        val encoded = document.select("script:containsData(toon_img)").firstOrNull()?.data()
            ?.substringAfter("'")?.substringBefore("'") ?: return emptyList()

        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))

        return pageListRegex.findAll(decoded).mapIndexed { i, matchResult ->
            val imageUrl = matchResult.destructured.component1().let { if (it.startsWith("http")) it else baseUrl + it }
            Page(i, imageUrl = imageUrl)
        }.toList()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val path = url.encodedPath
        if (path.isBlank() || path == "/" || path.startsWith("/bbs/") || path.startsWith("/css/") || path.startsWith("/js/")) return null
        val manga = SManga.create().apply {
            this.url = path
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
        private val pageListRegex = Regex("""src="([^"]*)"""")
    }
}
