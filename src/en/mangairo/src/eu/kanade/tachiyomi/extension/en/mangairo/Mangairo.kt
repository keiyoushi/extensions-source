package eu.kanade.tachiyomi.extension.en.mangairo

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Mangairo : MangaBox("Mangairo", "https://h.mangairo.com", "en", SimpleDateFormat("MMM-dd-yy", Locale.ENGLISH)) {
    override val popularUrlPath = "manga-list/type-topview/ctg-all/state-all/page-"
    override fun popularMangaSelector() = "div.story-item"
    override val latestUrlPath = "manga-list/type-latest/ctg-all/state-all/page-"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/list/$simpleQueryPath${normalizeSearchQuery(query)}?page=$page", headers)
    }

    override fun searchMangaSelector() = "div.story-item"
    override fun searchMangaFromElement(element: Element): SManga = mangaFromElement(element, "h2 a")
    override fun searchMangaNextPageSelector() = "div.group-page a.select + a:not(.go-p-end)"
    override val mangaDetailsMainSelector = "${super.mangaDetailsMainSelector}, div.story_content"
    override val thumbnailSelector = "${super.thumbnailSelector}, div.story_info_left img"
    override val descriptionSelector = "${super.descriptionSelector}, div#story_discription p"
    override fun chapterListSelector() = "${super.chapterListSelector()}, div#chapter_list li"
    override val alternateChapterDateSelector = "p"
    override val pageListSelector = "${super.pageListSelector}, div.panel-read-story img"

    // will have to write a separate searchMangaRequest to get filters working for this source
    override fun getFilterList() = FilterList()
}
