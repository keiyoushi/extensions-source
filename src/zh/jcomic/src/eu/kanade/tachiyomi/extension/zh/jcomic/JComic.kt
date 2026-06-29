package eu.kanade.tachiyomi.extension.zh.jcomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class JComic : HttpSource() {

    override val baseUrl = "https://jcomic.net"

    override val lang = "zh"

    override val name = "JComic"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val origin = chain.request()
            chain.proceed(origin).also {
                if (it.code == 403 && origin.url.toString().contains("jcomic-content")) {
                    it.close()
                    throw IOException("图片已失效，请清除章节缓存后重试\n（因为链接有效期只有1分钟，建议以后下载完章节再看）")
                }
            }
        }.build()

    // Customize

    companion object {
        val SIZE_REGEX = Regex("\\((\\d+)\\)")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE)
    }

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/cat/%E9%9A%A8%E6%A9%9F/$page")

    override fun popularMangaParse(response: Response) = response.asJsoup().let { doc ->
        val mangas = doc.select(".container .col-lg-4").map {
            val a = it.selectFirst("> a:nth-child(1)")!!
            val tags = it.select(".list-content > a").drop(1)
            val time = DATE_FORMAT.tryParse(it.selectFirst(".comic-date")!!.text().substringAfter("最後更新: "))
            val name = it.selectFirst(".comic-title")!!.text()
            SManga.create().apply {
                url = a.attr("href") + "#$time"
                thumbnail_url = a.selectFirst("img")!!.absUrl("src")
                title = SIZE_REGEX.replace(name) { m ->
                    description = "共 ${m.groups[1]?.value} 页\n"
                    ""
                }
                author = tags.firstOrNull()?.text()
                genre = tags.drop(1).joinToString(transform = Element::text)
                initialized = true
            }
        }
        MangasPage(mangas, doc.selectFirst(".pagination > li.active:last-child") == null)
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/cat/%E6%9C%80%E8%BF%91%E6%9B%B4%E6%96%B0/$page")

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Page

    override fun getFilterList() = FilterList(
        object : Filter.Select<String>("搜索类型", arrayOf("默认", "作者")) {
            override fun toString(): String = arrayOf("search", "author", "cat")[state]
        },
        object : Filter.Select<String>("分类（搜索关键字时无效）", arrayOf("全彩", "長篇", "單行本", "同人", "短篇", "Cosplay", "歐美", "WEBTOON", "圓神領域", "碧藍幻想", "CG雜圖", "英語 ENG", "生肉", "純愛", "百合花園", "耽美花園", "偽娘哲學", "後宮閃光", "扶他樂園", "姐姐系", "妹妹系", "SM", "性轉換", "足の恋", "重口地帶", "人妻", "NTR", "強暴", "非人類", "艦隊收藏", "Love Live", "SAO 刀劍神域", "Fate", "東方", "禁書目錄")) {
            override fun toString(): String = values[state]
        },
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        return if (query.isNotBlank()) {
            GET(url.addPathSegments("${filters.first()}/$query/$page").build())
        } else {
            GET(url.addPathSegments("cat/${filters[1]}/$page").build())
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga Detail Page

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url.replace("/page", "/eps"))

    override fun mangaDetailsParse(response: Response) = response.asJsoup().select(".col-md-6:nth-child(1)").let { e ->
        SManga.create().apply { thumbnail_url = e.selectFirst("img")!!.absUrl("src") }
    }

    // Catalog Page

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = if (manga.url.contains("/page")) {
        Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "单章节"
                    date_upload = manga.url.substringAfter("#").toLong()
                },
            ),
        )
    } else {
        client.newCall(chapterListRequest(manga)).asObservableSuccess().map(::chapterListParse)
    }

    override fun chapterListParse(response: Response) = response.asJsoup().let { doc ->
        val time = response.request.url.fragment!!.toLong()
        doc.select(".col-md-6:nth-child(2) a").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.text()
                date_upload = time
            }
        }
    }.reversed()

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().select(".comic-thumb").mapIndexed { i, image ->
        Page(i, imageUrl = image.attr("src"))
    }

    // Image

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
