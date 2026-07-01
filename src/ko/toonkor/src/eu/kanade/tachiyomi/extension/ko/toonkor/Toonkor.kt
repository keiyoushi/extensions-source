package eu.kanade.tachiyomi.extension.ko.toonkor

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Toonkor : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_POPULAR", headers)

    override fun popularMangaParse(response: Response): MangasPage {
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

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl$WEBTOONS_PATH$ALL_STATUS_PATH$SORT_LATEST", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }

        val type = filterList.firstInstanceOrNull<TypeFilter>()
        val status = filterList.firstInstanceOrNull<StatusFilter>()
        val sort = filterList.firstInstanceOrNull<SortFilter>()

        val requestPath = when {
            query.isNotEmpty() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            else -> "${type?.toUriPart() ?: ""}${status?.toUriPart() ?: ""}${sort?.toUriPart() ?: ""}"
        }

        return GET(baseUrl + requestPath, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.select("table.bt_view1")) {
                title = select("td.bt_title").text()
                author = select("td.bt_label span.bt_data").text()
                description = select("td.bt_over").text()
                thumbnail_url = select("td.bt_thumb img").firstOrNull()?.attr("abs:src")
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("table.web_list tr:has(td.content__title)").map { element ->
            SChapter.create().apply {
                element.select("td.content__title").let {
                    url = it.attr("data-role")
                    name = it.text()
                }
                date_upload = dateFormat.tryParse(element.select("td.episode__index").text())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val encoded = document.select("script:containsData(toon_img)").firstOrNull()?.data()
            ?.substringAfter("'")?.substringBefore("'") ?: return emptyList()

        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))

        return pageListRegex.findAll(decoded).mapIndexed { i, matchResult ->
            val imageUrl = matchResult.destructured.component1().let { if (it.startsWith("http")) it else baseUrl + it }
            Page(i, imageUrl = imageUrl)
        }.toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: can't combine with text search!"),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        private val pageListRegex = Regex("""src="([^"]*)"""")
    }
}
