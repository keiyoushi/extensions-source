package eu.kanade.tachiyomi.extension.all.misskon

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MissKon() : SimpleParsedHttpSource() {

    override val baseUrl = "https://misskon.com"
    override val lang = "all"
    override val name = "MissKon"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 10, 1, TimeUnit.SECONDS)
        .setRandomUserAgent(UserAgentType.MOBILE)
        .build()

    override fun simpleMangaSelector() = "article.item-list"
    override fun simpleMangaFromElement(element: Element): SManga {
        val titleEL = element.select(".post-box-title")

        val manga = SManga.create()
        manga.title = titleEL.text()
        manga.thumbnail_url = element.select(".post-thumbnail img").attr("data-src")
        manga.setUrlWithoutDomain(titleEL.select("a").attr("href").substring(baseUrl.length))
        return manga
    }

    override fun simpleNextPageSelector(): String? = null

    // region popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top3/")
    // endregion

    // region latest
    override fun latestUpdatesRequest(page: Int): Request {
        val url: String = if (page <= 1) {
            baseUrl
        } else {
            "$baseUrl/page/$page"
        }
        return GET(url)
    }

    override fun latestUpdatesNextPageSelector() = ".current + a.page"
    // endregion

    // region Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filter = filters.findInstance<TagFilter>()!!
        return if (filter.isSelected()) {
            GET(filter.selected.url)
        } else {
            GET("$baseUrl/page/$page/?s=$query")
        }
    }

    override fun searchMangaNextPageSelector() = "div.content > div.pagination > span.current + a"
    override fun searchMangaSelector() = "article.item-list"
    // endregion

    // region Details
    override fun mangaDetailsParse(document: Document): SManga {
        val postInnerEl = document.select("article > .post-inner")

        val manga = SManga.create()
        manga.title = postInnerEl.select(".post-title").text()
        manga.description = ""
        manga.genre = postInnerEl.select(".post-tag > a").joinToString(", ") { it.text() }
        return manga
    }

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element): SChapter {
        val dataSrc = element.selectFirst(".entry img")!!.attr("data-src")
        val dataRegex = Regex("^.+(\\d{4}/\\d{2}/\\d{2}).+$")
        val dateStr = if (dataSrc.matches(dataRegex)) {
            dataSrc.replace(dataRegex, "$1")
        } else {
            "1997/01/01"
        }

        val chapter = SChapter.create()
        chapter.chapter_number = 0F
        chapter.setUrlWithoutDomain(element.selectFirst("link[rel=\"canonical\"]")!!.attr("href"))
        chapter.name = dateStr
        chapter.date_upload = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).parse(dateStr)!!.time
        return chapter
    }
    // endregion

    // region Pages
    override fun pageListParse(document: Document): List<Page> {
        val basePageUrl = document.selectFirst("link[rel=\"canonical\"]")!!.attr("href")

        val pages = mutableListOf<Page>()
        document.select("div.post-inner div.page-link:nth-child(1) .post-page-numbers")
            .forEachIndexed { index, pageEl ->
                val doc = when (index) {
                    0 -> document
                    else -> {
                        val url = "$basePageUrl${pageEl.text()}/"
                        client.newCall(GET(url)).execute().asJsoup()
                    }
                }
                doc.select("div.post-inner > div.entry > p > img")
                    .map { it.attr("data-src") }
                    .forEach { pages.add(Page(pages.size, "", it)) }
            }
        return pages
    }
    // endregion

    // region Filters
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        initTagFilter(),
    )

    class Tag(private val name: String, val url: String) {
        override fun toString() = this.name
    }

    class TagFilter(name: String, tags: List<Tag>) : Filter.Select<Tag>(name, tags.toTypedArray()) {
        val selected: Tag
            get() = values[state]

        fun isSelected(): Boolean {
            return state > 0
        }
    }

    private fun initTagFilter(): TagFilter {
        val options = mutableListOf(Tag("unselected", ""))
        // top
        options.addAll(
            listOf(
                Tag("Top 3 days", "https://misskon.com/top3/"),
                Tag("Top 7 days", "https://misskon.com/top7/"),
                Tag("Top 30 days", "https://misskon.com/top30/"),
                Tag("Top 60 days", "https://misskon.com/top60/"),
            ),
        )
        // Chinese
        options.addAll(
            listOf(
                Tag("Chinese:[MTCos] 喵糖映画", "https://misskon.com/tag/mtcos/"),
                Tag("Chinese:BoLoli", "https://misskon.com/tag/bololi/"),
                Tag("Chinese:CANDY", "https://misskon.com/tag/candy/"),
                Tag("Chinese:FEILIN", "https://misskon.com/tag/feilin/"),
                Tag("Chinese:FToow", "https://misskon.com/tag/ftoow/"),
                Tag("Chinese:GIRLT", "https://misskon.com/tag/girlt/"),
                Tag("Chinese:HuaYan", "https://misskon.com/tag/huayan/"),
                Tag("Chinese:HuaYang", "https://misskon.com/tag/huayang/"),
                Tag("Chinese:IMISS", "https://misskon.com/tag/imiss/"),
                Tag("Chinese:ISHOW", "https://misskon.com/tag/ishow/"),
                Tag("Chinese:JVID", "https://misskon.com/tag/jvid/"),
                Tag("Chinese:KelaGirls", "https://misskon.com/tag/kelagirls/"),
                Tag("Chinese:Kimoe", "https://misskon.com/tag/kimoe/"),
                Tag("Chinese:LegBaby", "https://misskon.com/tag/legbaby/"),
                Tag("Chinese:MF", "https://misskon.com/tag/mf/"),
                Tag("Chinese:MFStar", "https://misskon.com/tag/mfstar/"),
                Tag("Chinese:MiiTao", "https://misskon.com/tag/miitao/"),
                Tag("Chinese:MintYe", "https://misskon.com/tag/mintye/"),
                Tag("Chinese:MISSLEG", "https://misskon.com/tag/missleg/"),
                Tag("Chinese:MiStar", "https://misskon.com/tag/mistar/"),
                Tag("Chinese:MTMeng", "https://misskon.com/tag/mtmeng/"),
                Tag("Chinese:MyGirl", "https://misskon.com/tag/mygirl/"),
                Tag("Chinese:PartyCat", "https://misskon.com/tag/partycat/"),
                Tag("Chinese:QingDouKe", "https://misskon.com/tag/qingdouke/"),
                Tag("Chinese:RuiSG", "https://misskon.com/tag/ruisg/"),
                Tag("Chinese:SLADY", "https://misskon.com/tag/slady/"),
                Tag("Chinese:TASTE", "https://misskon.com/tag/taste/"),
                Tag("Chinese:TGOD", "https://misskon.com/tag/tgod/"),
                Tag("Chinese:TouTiao", "https://misskon.com/tag/toutiao/"),
                Tag("Chinese:TuiGirl", "https://misskon.com/tag/tuigirl/"),
                Tag("Chinese:Tukmo", "https://misskon.com/tag/tukmo/"),
                Tag("Chinese:UGIRLS", "https://misskon.com/tag/ugirls/"),
                Tag("Chinese:UGIRLS - Ai You Wu App", "https://misskon.com/tag/ugirls-ai-you-wu-app/"),
                Tag("Chinese:UXING", "https://misskon.com/tag/uxing/"),
                Tag("Chinese:WingS", "https://misskon.com/tag/wings/"),
                Tag("Chinese:XiaoYu", "https://misskon.com/tag/xiaoyu/"),
                Tag("Chinese:XingYan", "https://misskon.com/tag/xingyan/"),
                Tag("Chinese:XIUREN", "https://misskon.com/tag/xiuren/"),
                Tag("Chinese:XR Uncensored", "https://misskon.com/tag/xr-uncensored/"),
                Tag("Chinese:YouMei", "https://misskon.com/tag/youmei/"),
                Tag("Chinese:YouMi", "https://misskon.com/tag/youmi/"),
                Tag("Chinese:YouMi尤蜜", "https://misskon.com/tag/youmiapp/"),
                Tag("Chinese:YouWu", "https://misskon.com/tag/youwu/"),
            ),
        )
        // Korean
        options.addAll(
            listOf(
                Tag("Korean:AG", "https://misskon.com/tag/ag/"),
                Tag("Korean:Bimilstory", "https://misskon.com/tag/bimilstory/"),
                Tag("Korean:BLUECAKE", "https://misskon.com/tag/bluecake/"),
                Tag("Korean:CreamSoda", "https://misskon.com/tag/creamsoda/"),
                Tag("Korean:DJAWA", "https://misskon.com/tag/djawa/"),
                Tag("Korean:Espacia Korea", "https://misskon.com/tag/espacia-korea/"),
                Tag("Korean:Fantasy Factory", "https://misskon.com/tag/fantasy-factory/"),
                Tag("Korean:Fantasy Story", "https://misskon.com/tag/fantasy-story/"),
                Tag("Korean:Glamarchive", "https://misskon.com/tag/glamarchive/"),
                Tag("Korean:HIGH FANTASY", "https://misskon.com/tag/high-fantasy/"),
                Tag("Korean:KIMLEMON", "https://misskon.com/tag/kimlemon/"),
                Tag("Korean:KIREI", "https://misskon.com/tag/kirei/"),
                Tag("Korean:KiSiA", "https://misskon.com/tag/kisia/"),
                Tag("Korean:Korean Realgraphic", "https://misskon.com/tag/korean-realgraphic/"),
                Tag("Korean:Lilynah", "https://misskon.com/tag/lilynah/"),
                Tag("Korean:Lookas", "https://misskon.com/tag/lookas/"),
                Tag("Korean:Loozy", "https://misskon.com/tag/loozy/"),
                Tag("Korean:Moon Night Snap", "https://misskon.com/tag/moon-night-snap/"),
                Tag("Korean:Paranhosu", "https://misskon.com/tag/paranhosu/"),
                Tag("Korean:PhotoChips", "https://misskon.com/tag/photochips/"),
                Tag("Korean:Pure Media", "https://misskon.com/tag/pure-media/"),
                Tag("Korean:PUSSYLET", "https://misskon.com/tag/pussylet/"),
                Tag("Korean:SAINT Photolife", "https://misskon.com/tag/saint-photolife/"),
                Tag("Korean:SWEETBOX", "https://misskon.com/tag/sweetbox/"),
                Tag("Korean:UHHUNG MAGAZINE", "https://misskon.com/tag/uhhung-magazine/"),
                Tag("Korean:UMIZINE", "https://misskon.com/tag/umizine/"),
                Tag("Korean:WXY ENT", "https://misskon.com/tag/wxy-ent/"),
                Tag("Korean:Yo-U", "https://misskon.com/tag/yo-u/"),
            ),
        )
        // Other
        options.addAll(
            listOf(
                Tag("Other:AI Generated", "https://misskon.com/tag/ai-generated/"),
                Tag("Other:Cosplay", "https://misskon.com/tag/cosplay/"),
                Tag("Other:JP", "https://misskon.com/tag/jp/"),
                Tag("Other:JVID", "https://misskon.com/tag/jvid/"),
                Tag("Other:Patreon", "https://misskon.com/tag/patreon/"),
            ),
        )

        return TagFilter("Category", options)
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
    // endregion
}
