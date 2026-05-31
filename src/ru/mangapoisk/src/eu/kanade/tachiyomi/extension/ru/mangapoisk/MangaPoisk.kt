package eu.kanade.tachiyomi.extension.ru.mangapoisk

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

private val chapterRegex = Regex("""Глава\s(\d+)""", RegexOption.IGNORE_CASE)
private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))

class MangaPoisk : HttpSource() {
    override val name = "MangaPoisk"

    override val baseUrl = "https://mangap.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?sortBy=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga?sortBy=-last_chapter_at&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        val filterList = filters.ifEmpty { getFilterList() }

        filterList.firstInstanceOrNull<OrderBy>()?.let { orderBy ->
            val ord = arrayOf("-year", "popular", "name", "-published_at", "-last_chapter_at")[orderBy.state!!.index]
            val ordRev = arrayOf("year", "-popular", "-name", "published_at", "last_chapter_at")[orderBy.state!!.index]
            url.addQueryParameter("sortBy", if (orderBy.state!!.ascending) ordRev else ord)
        }

        filterList.firstInstanceOrNull<StatusList>()?.state?.filter { it.state }?.forEach { status ->
            url.addQueryParameter("translated[]", status.id)
        }

        filterList.firstInstanceOrNull<GenreList>()?.state?.filter { it.state }?.forEach { genre ->
            url.addQueryParameter("genres[]", genre.id)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isSearch = response.request.url.queryParameter("q") != null
        val selector = if (isSearch) "article.card" else ".manga-card"

        val mangas = document.select(selector).mapNotNull { element ->
            val urlElement = if (isSearch) element.selectFirst("a.card-about") else element.selectFirst("a")
            if (urlElement == null) return@mapNotNull null

            SManga.create().apply {
                thumbnail_url = element.selectFirst("a > img")?.let { getImage(it) } ?: ""
                setUrlWithoutDomain(urlElement.attr("href"))

                title = if (isSearch) {
                    element.selectFirst("a > h2.entry-title")?.text()?.substringBefore("/") ?: ""
                } else {
                    urlElement.attr("title").substringBefore("/")
                }
            }
        }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun getImage(element: Element): String {
        val image = element.attr("abs:data-src")
        if (image.isNotEmpty()) return image
        return element.attr("abs:src")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.card:has(header)") ?: return SManga.create()

        return SManga.create().apply {
            title = infoElement.selectFirst(".text-base span")?.text() ?: ""
            genre = infoElement.select("span:contains(Жанр:) a").joinToString { it.text() }
            description = infoElement.select(".manga-description").text()
            status = parseStatus(infoElement.selectFirst("span:contains(Статус:)")?.text() ?: "")
            thumbnail_url = infoElement.selectFirst("img.w-full")?.attr("abs:src")
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Завершена") -> SManga.COMPLETED
        status.contains("Выпускается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val document = client.newCall(GET("$baseUrl${manga.url}?tab=chapters", headers)).execute().asJsoup()
        if (document.selectFirst(".text-md:contains(Главы удалены по требованию правообладателя)") != null) {
            throw Exception("Лицензировано - Нет глав")
        }

        val firstPageResponse = client.newCall(chapterListRequest(manga)).execute()
        val firstPageDocument = firstPageResponse.asJsoup()
        val chapters = mutableListOf<SChapter>()

        chapters.addAll(firstPageDocument.select(".chapter-item").mapNotNull { chapterFromElement(it) })

        val lastPage = firstPageDocument.select("li.page-item")
            .mapNotNull { it.text().toIntOrNull() }
            .maxOrNull() ?: 1

        for (page in 2..lastPage) {
            val response = client.newCall(chapterPageListRequest(manga, page)).execute()
            chapters.addAll(response.asJsoup().select(".chapter-item").mapNotNull { chapterFromElement(it) })
        }
        chapters
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}/chaptersList", headers)

    private fun chapterPageListRequest(manga: SManga, page: Int): Request = GET("$baseUrl${manga.url}/chaptersList?page=$page", headers)

    private fun chapterFromElement(element: Element): SChapter? {
        val title = element.selectFirst("span.chapter-title")?.text() ?: return null
        val urlElement = element.selectFirst("a") ?: return null

        return SChapter.create().apply {
            setUrlWithoutDomain(urlElement.attr("href"))
            name = urlElement.text()
            chapter_number = chapterRegex.find(title)?.groupValues?.get(1)?.toFloat() ?: -1F
            date_upload = element.selectFirst("span.chapter-date")?.text()?.let { parseDate(it) } ?: 0L
        }
    }

    private fun parseDate(dateStr: String): Long {
        val amount = dateStr.substringBefore(" ").toLongOrNull()
        return when {
            amount != null && dateStr.contains("минут") -> System.currentTimeMillis() - amount * 60 * 1000
            amount != null && dateStr.contains("час") -> System.currentTimeMillis() - amount * 60 * 60 * 1000
            amount != null && (dateStr.contains("дня") || dateStr.contains("дней")) -> System.currentTimeMillis() - amount * 24 * 60 * 60 * 1000
            else -> dateFormat.tryParse(dateStr)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.html().contains("text-error-500-400-token")) {
            throw Exception("Лицензировано - Глава удалена по требованию правообладателя.")
        }
        return document.select(".page-image").mapIndexed { index, element ->
            Page(index, imageUrl = getImage(element))
        }
    }

    override fun getFilterList() = FilterList(
        OrderBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
