package eu.kanade.tachiyomi.extension.ru.yagamiproject

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class YagamiProject : KeiSource() {
    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = searchMangaParse(client.get("$baseUrl/list-new/$page").asJsoup())

    private fun searchMangaParse(document: Document): MangasPage {
        val mangas = document.select(".list .group").map { element ->
            SManga.create().apply {
                element.selectFirst(".title a")!!.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                    val baseTitle = it.attr("title")
                    title = if (baseTitle.isEmpty()) it.text() else baseTitle.split(" / ").min()
                }
                thumbnail_url = element.select(".cover_mini > img").attr("abs:src").replace("thumb_", "")
            }
        }
        val hasNextPage = document.selectFirst(".panel_nav .button > a:last-child") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = searchMangaParse(client.get("$baseUrl/latest/$page").asJsoup())

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            return searchMangaParse(client.get("$baseUrl/reader/search/?s=$query&p=$page").asJsoup())
        }
        val activeFilters = if (filters.isEmpty()) getFilterList() else filters
        activeFilters.firstInstanceOrNull<CategoryList>()?.let { filter ->
            if (filter.state > 0) {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("tags")
                    addPathSegment(getCategoryList()[filter.state].name)
                    if (page > 1) addPathSegment(page.toString())
                }.build()
                return searchMangaParse(client.get(url).asJsoup())
            }
        }
        activeFilters.firstInstanceOrNull<FormatList>()?.let { filter ->
            if (filter.state > 0) {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment(getFormatList()[filter.state].query)
                    if (page > 1) addPathSegment(page.toString())
                }.build()
                return searchMangaParse(client.get(url).asJsoup())
            }
        }
        return getPopularManga(page)
    }

    // Deeplink
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "series" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[0]}/${url.pathSegments[1]}/"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga)
        val document = client.get(url).asJsoup()
        val newManga = mangaDetailsParse(document, manga.url)
        val newChapter = chapterListParse(document)
        return SMangaUpdate(newManga, newChapter)
    }

    // Details
    private fun mangaDetailsParse(document: Document, mangaUrl: String): SManga {
        val infoElement = document.select(".large.comic .info").first()!!
        return SManga.create().apply {
            url = mangaUrl
            val titleStr = document.select("title").text()
                .substringBefore(" :: Yagami").split(" :: ").sorted()
            title = titleStr.first().replace(":: ", "")
            thumbnail_url = document.selectFirst(".cover img")!!.attr("abs:src")
            author = infoElement.selectFirst("li:contains(Автор(ы):)")?.text()
                ?.substringAfter("Автор(ы): ")?.split(" / ")?.min()?.replace("N/A", "")
            artist = infoElement.selectFirst("li:contains(Художник(и):)")?.text()
                ?.substringAfter("Художник(и): ")?.split(" / ")?.min()?.replace("N/A", "")
            status = when (infoElement.select("li:contains(Статус перевода:) span").first()?.text()) {
                "онгоинг" -> SManga.ONGOING
                "активный" -> SManga.ONGOING
                "завершён" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = infoElement.select("li:contains(Жанры:)").first()?.text()
                ?.substringAfter("Жанры: ")

            val altName = infoElement.selectFirst("li:contains(Название:)")?.textNodes()?.let { text ->
                text.filter { it.text() != ":" }.joinToString { "\n- ${it.text()}" }
            }
            val descriptionElem = infoElement.selectFirst("li:contains(Описание:)")?.ownText()?.substringAfter(":")?.trim()

            description = buildString {
                append(titleStr.last().replace(":: ", ""))
                descriptionElem?.let { append("\n$it") }
                altName?.let { append("\nАльтернативные названия:$it") }
            }
        }
    }

    // Chapters
    private fun chapterListParse(document: Document): List<SChapter> = document.select(".list .element").map { element ->
        SChapter.create().apply {
            val chapter = element.select(".title a").first()!!
            val chapterScanDate = element.select(".meta_r")
            name = chapter.attr("title").ifBlank { chapter.text() }
            chapter_number = name.substringBefore(":").substringAfterLast(" ")
                .substringAfterLast("№").substringAfterLast("#").toFloatOrNull()
                ?: chapter.attr("href").substringBeforeLast("/").substringAfterLast("/").toFloatOrNull()
                ?: -1f
            setUrlWithoutDomain(chapter.absUrl("href"))
            date_upload = parseDate(chapterScanDate.text().substringAfter(", "))
            scanlator = chapterScanDate.select("a").takeIf { it.isNotEmpty() }
                ?.joinToString(" / ") { it.text() }
        }
    }

    private fun parseDate(date: String): Long = when (date) {
        "Сегодня" -> System.currentTimeMillis()
        "Вчера" -> System.currentTimeMillis() - 24 * 60 * 60 * 1000
        else -> dateFormat.tryParseDate(date)
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val initialDoc = client.get(getChapterUrl(chapter)).asJsoup()
        val doc = if (initialDoc.selectFirst(".info")?.text()?.contains("mature contents") == true) {
            client.post(getChapterUrl(chapter), adultBody).asJsoup()
        } else {
            initialDoc
        }

        val webtoonsel = doc.select(".web_pictures img.web_img")

        return if (webtoonsel.isEmpty()) {
            doc.select(".dropdown li a").map {
                Page(it.text().substringAfter("Стр. ").toInt(), it.absUrl("href"))
            }
        } else {
            webtoonsel.mapIndexed { i, img -> Page(i, imageUrl = img.attr("abs:src")) }
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        val defaultImg = document.select("#page img").attr("abs:src")
        return if (defaultImg.contains("string(1)")) {
            document.select("#get_download").first()!!.absUrl("href")
        } else {
            defaultImg
        }
    }

    // Filters
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("ПРИМЕЧАНИЕ: Фильтры исключают другдруга!"),
        CategoryList(getCategoryList().map { it.name }.toTypedArray()),
        FormatList(getFormatList().map { it.name }.toTypedArray()),
    )

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val adultBody = FormBody.Builder().add("adult", "true").build()
    }
}
