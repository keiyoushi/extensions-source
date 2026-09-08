package eu.kanade.tachiyomi.extension.ru.mangapoisk

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaPoisk : KeiSource() {

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "popular")

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "-last_chapter_at")

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            if (query.length < 3) {
                throw Exception("Запрос должен содержать не менее 3 символов. / The query must contain at least 3 characters")
            }
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            client.get(url).use {
                return mangaParse(it)
            }
        }

        return makeCatalogRequest(page, "likes", filters)
    }

    // ============================== Search Utilities ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String, filters: FilterList? = null): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            filters?.forEach { filter ->
                when (filter) {
                    is OrderBy -> addQueryParameter("sortBy", "${filter.order}${filter.selected}")
                    is StatusList -> filter.checked?.let { addQueryParameter("translated", "[${it.joinToString(",")}]") }
                    is GenresFilter -> {
                        filter.included?.let { addQueryParameter("genres", "[${it.joinToString(",")}]") }
                        filter.excluded?.let { addQueryParameter("genres-exclude", "[${it.joinToString(",")}]") }
                    }
                    else -> {}
                }
            }

            if (filters == null) {
                addQueryParameter("sortBy", sortBy)
            }
            addQueryParameter("page", page.toString())
        }.build()

        client.get(url).use {
            return mangaParse(it)
        }
    }

    private fun mangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isSearch = response.request.url.queryParameter("q") != null
        val selector = if (isSearch) "article.card" else ".manga-card"
        val hasNextPage = if (isSearch) {
            document.selectFirst("ul.pagination li a[aria-label*=Вперёд]:not([aria-disabled=true])") != null
        } else {
            document.selectFirst("ul li:contains(Вперёд) a") != null
        }

        val mangas = document.select(selector).mapNotNull { element ->
            val urlElement = if (isSearch) element.selectFirst("a.card-about") else element.selectFirst("a")
            if (urlElement == null) return@mapNotNull null
            val titleParsed = if (isSearch) {
                element.selectFirst("div.post-description p.card-title")?.text()?.takeIf { it.isNotBlank() }
                    ?: element.selectFirst("a > h2.entry-title")?.text()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            } else {
                urlElement.attr("title").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            }.substringBefore("/")

            SManga.create().apply {
                thumbnail_url = element.selectFirst("a > img")?.imgAttr() ?: ""
                setUrlWithoutDomain(urlElement.attr("href"))
                title = titleParsed
            }
        }

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga") {
            val tmpManga = SManga.create().apply {
                this.url = url.encodedPath
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaSlug = manga.url
        val mangaAsync = async {
            if (fetchDetails) {
                val requestUrl = "$baseUrl${manga.url}"
                val document = client.get(requestUrl).use { it.asJsoup() }
                val infoElement = document.selectFirst("div.card:has(header)") ?: throw Exception("Не получилось найти информацию о манге")

                SManga.create().apply {
                    url = mangaSlug
                    title = infoElement.selectFirst(".text-base span")?.text() ?: ""
                    genre = infoElement.select("span:contains(Жанр:) a").joinToString { it.text() }
                    description = infoElement.select(".manga-description").text()
                    status = parseStatus(infoElement.selectFirst("span:contains(Статус:)")?.text() ?: "")
                    thumbnail_url = infoElement.selectFirst("img.w-full")?.attr("abs:src")
                }
            } else {
                manga
            }
        }

        val chaptersAsync = async {
            if (fetchChapters) {
                val document = client.get("${baseUrl}$mangaSlug?tab=chapters").use { it.asJsoup() }
                if (document.selectFirst(".text-md:contains(Главы удалены по требованию правообладателя)") != null) {
                    throw Exception("Лицензировано - Нет глав")
                }

                val firstPageDocument = client.get("${baseUrl}$mangaSlug/chaptersList").use { it.asJsoup() }
                val chaptersList = mutableListOf<SChapter>()

                chaptersList.addAll(firstPageDocument.select(".chapter-item").mapNotNull { chapterFromElement(it) })

                val lastPage = firstPageDocument.select("li.page-item")
                    .mapNotNull { it.text().toIntOrNull() }
                    .maxOrNull() ?: 1

                for (page in 2..lastPage) {
                    val response = client.get("${baseUrl}$mangaSlug/chaptersList?page=$page").use { it.asJsoup() }
                    chaptersList.addAll(response.select(".chapter-item").mapNotNull { chapterFromElement(it) })
                }
                chaptersList
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // =========================== Chapters ============================
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

    // =========================== Pages ============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").use { it.asJsoup() }
        if (document.html().contains("text-error-500-400-token")) {
            throw Exception("Лицензировано - Глава удалена по требованию правообладателя.")
        }
        return document.select("img.page-image").mapIndexed { idx, element ->
            Page(idx, imageUrl = element.imgAttr())
        }
    }

    // =========================== Filters ============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        GenresFilter(),
        StatusList(),
    )

    // =========================== Utilities ============================
    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") && absUrl("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Завершена") -> SManga.COMPLETED
        status.contains("Выпускается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    private fun parseDate(dateStr: String): Long {
        val amount = dateStr.substringBefore(" ").toLongOrNull()
        return when {
            amount != null && dateStr.contains("минут") -> System.currentTimeMillis() - amount * 60 * 1000
            amount != null && dateStr.contains("час") -> System.currentTimeMillis() - amount * 60 * 60 * 1000
            amount != null && (dateStr.contains("дня") || dateStr.contains("дней")) -> System.currentTimeMillis() - amount * 24 * 60 * 60 * 1000
            else -> runCatching {
                LocalDate.parse(dateStr, dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }

    companion object {
        private val chapterRegex = Regex("""Глава\s(\d+)""", RegexOption.IGNORE_CASE)
        private val dateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("ru"))
    }
}
