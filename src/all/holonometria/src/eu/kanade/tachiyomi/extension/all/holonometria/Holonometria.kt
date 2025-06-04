package eu.kanade.tachiyomi.extension.all.holonometria

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Holonometria(
    override val lang: String,
    private val langPath: String = "$lang/",
) : ParsedHttpSource() {

    override val name = "HOLONOMETRIA"

    override val baseUrl = "https://holoearth.com/"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/${langPath}holonometria/manga", headers)

    override fun popularMangaSelector() = ".manga__item"
    override fun popularMangaNextPageSelector() = null

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.select(".manga__title").text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/${langPath}holonometria/manga", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val search = response.request.url.fragment!!

        val entries = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)
            .filter { it.title.contains(search, true) }

        return MangasPage(entries, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".alt-nav__met-sub-link.is-current").text()
        thumbnail_url = document.select(".manga-detail__thumb img").attr("abs:src")
        description = document.select(".manga-detail__caption").text()
        val info = document.select(".manga-detail__person").html().split("<br>")
        author = info.firstOrNull { desc -> manga.any { desc.contains(it, true) } }
            ?.substringAfter("：")
            ?.substringAfter(":")
            ?.trim()
            ?.replace("&amp;", "&")
        artist = info.firstOrNull { desc -> script.any { desc.contains(it, true) } }
            ?.substringAfter("：")
            ?.substringAfter(":")
            ?.trim()
            ?.replace("&amp;", "&")
    }

    override fun chapterListRequest(manga: SManga) =
        paginatedChapterListRequest(manga.url, 1)

    private fun paginatedChapterListRequest(mangaUrl: String, page: Int) =
        GET("$baseUrl$mangaUrl".removeSuffix("/") + if (page == 1) "/" else "/page/$page/", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaUrl = response.request.url.toString()
            .substringAfter(baseUrl)
            .substringBefore("page/")

        val chapters = document.select(chapterListSelector())
            .map(::chapterFromElement)
            .toMutableList()

        val lastPage = document.select(".pagenation-list a").last()
            ?.text()?.toIntOrNull() ?: return chapters

        for (page in 2..lastPage) {
            val request = paginatedChapterListRequest(mangaUrl, page)
            val newDocument = client.newCall(request).execute().asJsoup()

            val moreChapters = newDocument.select(chapterListSelector())
                .map(::chapterFromElement)

            chapters.addAll(moreChapters)
        }

        return chapters
    }

    override fun chapterListSelector() = ".manga-detail__list-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        val nameText = element.select(".manga-detail__list-title").text()
        name = nameText.split("【", "】")[1]
        date_upload = element.selectFirst(".manga-detail__list-date")?.text().parseDate()
        scanlator = "COVER Corporation"
        val number = name.replace("-", ".").split(" ")[1].toFloatOrNull()
        chapter_number = number ?: 0f
    }

    private fun String?.parseDate(): Long {
        return runCatching {
            dateFormat.parse(this!!)!!.time
        }.getOrDefault(0L)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".manga-detail__swiper-slide.swiper-slide img").mapIndexed { idx, img ->
            Page(idx, "", img.attr("abs:src"))
        }.reversed()
    }

    companion object {
        private val manga = listOf("manga", "gambar", "漫画")
        private val script = listOf("script", "naskah", "脚本")

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd", Locale.ENGLISH)
        }
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
