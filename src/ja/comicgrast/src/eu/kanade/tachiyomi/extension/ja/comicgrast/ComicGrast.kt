package eu.kanade.tachiyomi.extension.ja.comicgrast

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ComicGrast : KeiSource() {
    override val supportsLatest = false

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/comic/serial/$page").asJsoup().parseMangaList()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/comic/search/$page".toHttpUrl().newBuilder()
            .addQueryParameter("type", "0")
            .addQueryParameter("word", query)
            .build()
        return client.get(url).asJsoup().parseMangaList()
    }

    private fun Document.parseMangaList(): MangasPage {
        val mangas = select("ul.comicList > li").map { element ->
            SManga.create().apply {
                title = element.select("dl > dt > p.line-clamp.n2").text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
        val hasNextPage = selectFirst(".pageList li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".comicTit")!!.text()
            description = document.selectFirst(".comicStory .txtAcd_text_inner")?.text()
            author = document.select(".credit li").joinToString { it.text() }
            genre = document.select(".topCategoryTag ul li a").joinToString { it.text().removePrefix("#") }
            thumbnail_url = document.selectFirst(".serialMainImage")?.absUrl("src") ?: manga.thumbnail_url
        }

        val chapterList = document.select(".comicSerialList article a").mapNotNull { element ->
            val chapterUrl = element.absUrl("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(chapterUrl)
                name = element.select(".storyTitle").text()
                date_upload = dateFormat.tryParseDate(element.select(".update").text().replace(" 更新", ""), ZoneId.of("Asia/Tokyo"))
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val scriptData = document.selectFirst("#comic-data")?.data()
            ?: throw Exception("Comic data not found")

        val comicData = scriptData.parseAs<ComicData>()
        val contentUrl = "$baseUrl/img/serial-comic/${comicData.serialComicId}/${comicData.storyNumber}/content"
        val indexPages = client.get("$contentUrl/index.json").parseAs<List<ComicPage>>()

        return indexPages.mapIndexed { i, page ->
            val url = "$contentUrl/${page.name}".toHttpUrl().newBuilder()
                .addQueryParameter("seed", page.seed)
                .addQueryParameter("size", page.size.toString())
                .build()
                .toString()
            Page(i, imageUrl = url)
        }
    }
}
