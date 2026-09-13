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
        val filterList = filters.ifEmpty { getFilterList() }

        val type = filterList.firstInstanceOrNull<TypeFilter>()
        val status = filterList.firstInstanceOrNull<StatusFilter>()
        val sort = filterList.firstInstanceOrNull<SortFilter>()

        val requestPath = when {
            query.isNotEmpty() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            else -> "${type?.toUriPart() ?: ""}${status?.toUriPart() ?: ""}${sort?.toUriPart() ?: ""}"
        }

        return parseMangaList(client.get(baseUrl + requestPath))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.section-item-inner").map { element ->
            SManga.create().apply {
                element.select("div.section-item-title a").let {
                    title = it.select("h3").text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.select("img").attr("abs:src")
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
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        with(document.select("table.bt_view1")) {
            title = select("td.bt_title").text()
            author = select("td.bt_label span.bt_data").text()
            description = select("td.bt_over").text()
            thumbnail_url = select("td.bt_thumb img").firstOrNull()?.attr("abs:src")
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table.web_list tr:has(td.content__title)").map { element ->
        SChapter.create().apply {
            element.select("td.content__title").let {
                url = it.attr("data-role")
                name = it.text()
            }
            date_upload = dateFormat.tryParseDate(element.select("td.episode__index").text(), KOREA_ZONE)
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
        if (path.isBlank() || path == "/" || path.startsWith("/bbs/")) return null
        return try {
            val document = client.get(url).asJsoup()
            parseMangaDetails(document).apply {
                this.url = path
            }
        } catch (_: Exception) {
            null
        }
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
