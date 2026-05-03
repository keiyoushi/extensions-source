package eu.kanade.tachiyomi.extension.zh.favcomic

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class FavComic :
    HttpSource(),
    ConfigurableSource {

    override val baseUrl: String
        get() {
            val index = pref.getString(PREF_BASE_URL, "0")!!.toInt()
                .coerceAtMost(mirrorUrls.size - 1)
            return mirrorUrls[index]
        }

    override val lang = "zh"

    override val name = "喜漫漫画"

    override val supportsLatest = true

    private val pref by getPreferencesLazy()

    override val client = super.client.newBuilder().addInterceptor(ImageDecryptInterceptor()).build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Popular Page

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/rank".toHttpUrl().newBuilder()
            .addQueryParameter("range", pref.getString(PREF_RANK_TYPE, "1"))
            .addQueryParameter("comicType", pref.getString(PREF_MANGA_TYPE, "boy-1")!!.substringAfter('-'))
            .addQueryParameter("vip", "0")
        return GET(url.build())
    }

    override fun popularMangaParse(response: Response) = response.asJsoup().let { doc ->
        val mangas = doc.select(".rank_item > a").map { a ->
            val img = a.selectFirst(".cover > img")!!
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
                title = img.attr("alt")
                author = a.selectFirst(".author")!!.text()
                description = a.selectFirst(".brief")!!.text()
            }
        }
        MangasPage(mangas, false)
    }

    // Latest Page
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/${pref.getString(PREF_MANGA_TYPE, "boy-1")!!.substringBefore('-')}?page=$page")

    override fun latestUpdatesParse(response: Response) = response.asJsoup().let { doc ->
        val mangas = doc.select(".cover_box > a").map { a ->
            val img = a.selectFirst(".cover")!!
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
                title = a.attr("title")
            }
        }
        val e = doc.selectFirst(".pagination_box > .content_box > div:nth-last-child(2) > a")!!
        MangasPage(mangas, !e.hasClass("active"))
    }

    // Search Page

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val mangaTypeFilter = filters.firstInstance<MangaTypeFilter>()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaTypeFilter.toString())
            .addQueryParameter("keyword", query)
            .addQueryParameter("origin", filters[2].toString())
            .addQueryParameter("finished", filters[3].toString())
            .addQueryParameter("free", filters[4].toString())
            .addQueryParameter("sort", filters[5].toString())
            .addQueryParameter("page", page.toString())
        filters.firstInstance<TagGroup>().getTag(mangaTypeFilter.state)?.run { url.addQueryParameter("tag", this) }

        return GET(url.build())
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // Manga Detail Page

    override fun mangaDetailsParse(response: Response) = response.asJsoup().let { doc ->
        val img = doc.selectFirst(".comic_cover_box > .flex_box > img")!!
        val note = doc.selectFirst(".translation_agency_box")?.text()
        SManga.create().apply {
            setUrlWithoutDomain(doc.location())
            title = doc.selectFirst(".comic_title")!!.text()
            thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
            author = doc.selectFirst(".author")!!.text()
            description = doc.selectFirst(".intro_box > .txt")!!.text().substringAfter("作品介绍：") + (note?.let { "\n\n*$it*" } ?: "")
            status = when (doc.selectFirst(".state_box > span:nth-of-type(2)")?.text()) {
                "连载中" -> SManga.ONGOING
                "完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = doc.select(".tag_box a").joinToString(transform = Element::text)
        }
    }

    // Catalog Page

    override fun chapterListParse(response: Response) = response.asJsoup().let { doc ->
        doc.select(".catalog_box a").map { a ->
            val note = a.selectFirst("span:last-child")?.text()?.toFloatOrNull()
            SChapter.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                name = (note?.let { "\uD83E\uDE99 " } ?: "") + a.selectFirst(".title")!!.text()
                scanlator = note?.let { "￥$note" }
            }
        }
    }.reversed()

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().let { doc ->
        when (doc.selectFirst(".comic_chapter_box")!!.attr("code")) {
            "1" -> throw Exception("此话需在 WebView 中登录才能看")
            "3" -> throw Exception("金币不足，请充值")
            "4" -> throw Exception("请在 WebView 中付费解锁此话")
            "444" -> throw Exception("免费额度已用完，明天零点重置")
            else -> doc.select("#content > img").mapIndexed { i, img ->
                Page(i, imageUrl = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}")
            }
        }
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
