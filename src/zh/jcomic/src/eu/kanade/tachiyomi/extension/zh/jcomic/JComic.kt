package eu.kanade.tachiyomi.extension.zh.jcomic

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
import keiyoushi.utils.long
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class JComic : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val origin = chain.request()
            chain.proceed(origin).also {
                if (it.code == 403 && origin.url.toString().contains("jcomic-content")) {
                    it.close()
                    throw IOException("图片已失效，清除章节缓存后重试\n（链接有效期只有1分钟，建议以后下载完章节再看）")
                }
            }
        }
    }

    // Customize

    companion object {
        val SIZE_REGEX = Regex("\\((\\d+)\\)")
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE)
    }

    private fun mangaPageParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".container .col-lg-4").map {
            val a = it.selectFirst("> a:nth-child(1)")!!
            val tags = it.select(".list-content > a").drop(1)
            val time = DATE_FORMAT.tryParse(it.selectFirst(".comic-date")!!.text().substringAfter("最後更新: "))
            val name = it.selectFirst(".comic-title")!!.text()
            SManga.create().apply {
                url = a.attr("href")
                thumbnail_url = a.selectFirst("img")!!.absUrl("src")
                title = SIZE_REGEX.replace(name) { m ->
                    description = "共 ${m.groups[1]?.value} 页\n"
                    ""
                }
                author = tags.firstOrNull()?.text()
                genre = tags.drop(1).joinToString(transform = Element::text)
                memo = buildJsonObject { put("time", time) }
                initialized = true
            }
        }
        val hasNext = doc.select(".pagination > li.active").not(":last-child").isNotEmpty()
        return MangasPage(mangas, hasNext)
    }

    // Popular
    override suspend fun getPopularManga(page: Int) = mangaPageParse(client.get("$baseUrl/cat/%E9%9A%A8%E6%A9%9F/$page"))

    // Updates
    override suspend fun getLatestUpdates(page: Int) = mangaPageParse(client.get("$baseUrl/cat/%E6%9C%80%E8%BF%91%E6%9B%B4%E6%96%B0/$page"))

    // Search
    override fun getFilterList(data: JsonElement?) = FilterList(
        object : Filter.Select<String>("搜索类型", arrayOf("默认", "作者")) {
            override fun toString(): String = arrayOf("search", "author", "cat")[state]
        },
        object : Filter.Select<String>(
            "分类（搜索关键字时无效）",
            arrayOf(
                "全彩", "長篇", "單行本", "同人", "短篇", "Cosplay", "歐美",
                "WEBTOON", "圓神領域", "碧藍幻想", "CG雜圖", "英語 ENG",
                "生肉", "純愛", "百合花園", "耽美花園", "偽娘哲學", "後宮閃光",
                "扶他樂園", "姐姐系", "妹妹系", "SM", "性轉換", "足の恋",
                "重口地帶", "人妻", "NTR", "強暴", "非人類", "艦隊收藏",
                "Love Live", "SAO 刀劍神域", "Fate", "東方", "禁書目錄",
            ),
        ) {
            override fun toString() = values[state]
        },
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
        val finalUrl = if (query.isNotBlank()) {
            url.addPathSegments("${filters.first()}/$query/$page").build()
        } else {
            url.addPathSegments("cat/${filters[1]}/$page").build()
        }
        return mangaPageParse(client.get(finalUrl))
    }

    // Redundant

    override val supportsRelatedMangas = false

    override suspend fun getMangaByUrl(url: HttpUrl) = throw UnsupportedOperationException()

    override suspend fun fetchRelatedMangaList(manga: SManga) = throw UnsupportedOperationException()

    // Manga & Chapter
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = supervisorScope {
        val asyncManga = if (fetchDetails) {
            async {
                val doc = client.get(baseUrl + manga.url.replace("/page", "/eps")).asJsoup()
                SManga.create().apply {
                    thumbnail_url = doc.selectFirst(".col-md-6:nth-child(1) img")?.absUrl("src")
                }
            }
        } else {
            CompletableDeferred(manga)
        }

        val asyncChapters = if (fetchChapters) {
            async {
                val time = manga.memo["time"]!!.long
                if (manga.url.contains("/page")) {
                    listOf(
                        SChapter.create().apply {
                            url = manga.url
                            name = "单章节"
                            date_upload = time
                        },
                    )
                } else {
                    val doc = client.get(baseUrl + manga.url).asJsoup()
                    doc.select(".col-md-6:nth-child(2) a").map {
                        SChapter.create().apply {
                            url = it.attr("href")
                            name = it.text()
                            date_upload = time
                        }
                    }.reversed()
                }
            }
        } else {
            CompletableDeferred(chapters)
        }

        SMangaUpdate(asyncManga.await(), asyncChapters.await())
    }

    // Page
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url)
        return response.asJsoup().select(".comic-thumb").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("src"))
        }
    }
}
