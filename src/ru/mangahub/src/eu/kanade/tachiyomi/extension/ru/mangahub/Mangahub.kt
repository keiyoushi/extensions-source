package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangahub : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addCookie { listOf("confirm_age" to "1") }
        rateLimit(2)
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("rating", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest("update", page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("rating", page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            var sorted = sortBy
            if (query?.isNotBlank() == true) {
                addPathSegment("search")
                addPathSegment("title")
                addQueryParameter("query", query)
            } else {
                addPathSegment("explore")
                val orderedFilters = SearchFilters()
                filters?.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> orderedFilters.genres = filter.toPathSegment("genres")
                        is TagsFilter -> orderedFilters.tags = filter.toPathSegment("tags")
                        is TypeFilter -> orderedFilters.type = filter.toPathSegment("type")
                        is StatusFilter -> orderedFilters.status = filter.toPathSegment("status")
                        is TranslationStatusFilter -> orderedFilters.translation = filter.toPathSegment("translation")
                        is FormatFilter -> orderedFilters.format = filter.toPathSegment("formats")
                        is AgeFilter -> orderedFilters.age = filter.toPathSegment("age")
                        is CountryFilters -> orderedFilters.country = filter.toPathSegment("country")
                        is RatingRangeFilter -> orderedFilters.rating = filter.toPathSegment("rating", isRating = true)
                        is YearRangeFilter -> orderedFilters.year = filter.toPathSegment("year")
                        is ChaptersRangeFilter -> orderedFilters.items = filter.toPathSegment("items")
                        is OrderBy -> filter.selected?.let { sorted = it }
                        else -> {}
                    }
                }

                // Path segments should follow order set in `toSegments()`, or site will return 404
                orderedFilters.toSegments().forEach { addPathSegment(it) }
                addPathSegment("sort-is-$sorted")
            }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return client.get(url).use { response ->
            val document = response.asJsoup()
            val mangas = document.select("div.item-grid").map { element ->
                val aTag = element.selectFirst("a.fw-medium")!!
                SManga.create().apply {
                    thumbnail_url = element.selectFirst("img.item-grid-image")?.attr("abs:src")
                    title = aTag.text()
                    setUrlWithoutDomain(aTag.absUrl("href"))
                }
            }
            val hasNextPage = document.selectFirst(".pagination .page-item:last-child a") != null
            MangasPage(mangas, hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "title" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = "/title/${url.pathSegments[1]}"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Manga ======================================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaUrl = manga.url
        val mangaAsync = async {
            if (fetchDetails) {
                val url = "$baseUrl$mangaUrl"
                val document = client.get(url).asJsoup()
                mangaDetailsParse(document, mangaUrl)
            } else {
                manga
            }
        }
        val chaptersAsync = async {
            if (fetchChapters) {
                val url = "$baseUrl$mangaUrl/chapters"
                val document = client.get(url).asJsoup()
                val translators = manga.memo["translators"]?.string
                chapterListParse(document, translators)
            } else {
                chapters
            }
        }
        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // =============================== Manga Utilities ===============================
    private fun mangaDetailsParse(document: Document, mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = document.selectFirst(".detail-panel h1")!!.text()

        author = document.getAttrValues("Автор", "Сценарист")
        artist = document.getAttrValues("Художник")

        genre = document.select(".tags a").joinToString { it.text().removePrefix("#") }

        description = document.selectFirst(".markdown-style.text-expandable-content")?.text()

        val statusElement = document.selectFirst(".attr-name:contains(Томов) + .attr-value")?.text()
        status = when {
            statusElement?.contains("продолжается") == true -> SManga.ONGOING
            statusElement?.contains("приостановлен") == true -> SManga.ON_HIATUS
            statusElement?.contains("завершен") == true || statusElement?.contains("выпуск прекращён") == true ->
                if (document.selectFirst(".attr-name:contains(Перевод) + .attr-value")?.text()?.contains("Завершен") == true) {
                    SManga.COMPLETED
                } else {
                    SManga.PUBLISHING_FINISHED
                }
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("img.cover-detail")?.absUrl("src")

        val translators = document.getAttrValues("Переводчик")
        if (!translators.isNullOrBlank()) {
            memo = buildJsonObject {
                put("translators", translators)
            }
        }
    }

    private fun Document.getAttrValues(vararg attrNames: String): String? = attrNames.flatMap { name ->
        select(".attr-name:contains($name) + .attr-value a").map {
            it.text().removeSuffix(",")
        }
    }
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString()
        .ifBlank { null }

    // =============================== Chapters ===============================
    private fun chapterListParse(document: Document, translators: String?): List<SChapter> = document.select(".detail-items .detail-item").map { element ->
        val urlElement = element.selectFirst("div.align-items-center > a")!!
        SChapter.create().apply {
            name = urlElement.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst("div.text-muted")?.text())
            setUrlWithoutDomain(urlElement.absUrl("href"))
            chapter_number = chapterNumberRegex.find(name)?.groupValues[1]?.toFloatOrNull() ?: -1f
            if (!translators.isNullOrBlank()) scanlator = translators
        }
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

        val images = document.select("img.reader-viewer-img")
        return images.mapIndexed { i, img ->
            val url = img.attr("data-src").let { if (it.startsWith("//")) "https:$it" else it }
            Page(i, imageUrl = url)
        }
    }

    // =============================== Filters ===============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = client.get("$baseUrl/explore").asJsoup()

        return SearchData(
            genres = data.getFilter("genres"),
            tags = data.getFilter("tags"),
            type = data.getFilter("type"),
            status = data.getFilter("status"),
            translation = data.getFilter("statusTranslation"),
            format = data.getFilter("formats"),
            age = data.getFilter("ageRating"),
            country = data.getFilter("country"),
            sort = data.select(".select-menu-list .select-menu-item").mapNotNull { element ->
                val value = element.selectFirst("input")?.attr("value")?.trim() ?: return@mapNotNull null
                val label = element.ownText().trim()
                label to value
            },
        ).toJsonElement()
    }

    private fun Document.getFilter(query: String): List<Pair<String, String>> = select("#filter_$query .filter-item").mapNotNull { element ->
        val value = element.selectFirst("input")?.attr("value")?.trim() ?: return@mapNotNull null
        val label = element.selectFirst("label")?.text() ?: return@mapNotNull null
        label to value
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        data?.parseAs<SearchData>()?.let {
            if (it.sort?.isNotEmpty() == true) filters.add(OrderBy(it.sort))
            if (it.genres?.isNotEmpty() == true) filters.add(GenreFilter(it.genres))
            if (it.tags?.isNotEmpty() == true) filters.add(TagsFilter(it.tags))
            if (it.type?.isNotEmpty() == true) filters.add(TypeFilter(it.type))
            if (it.status?.isNotEmpty() == true) filters.add(StatusFilter(it.status))
            if (it.translation?.isNotEmpty() == true) filters.add(TranslationStatusFilter(it.translation))
            if (it.format?.isNotEmpty() == true) filters.add(FormatFilter(it.format))
            if (it.age?.isNotEmpty() == true) filters.add(AgeFilter(it.age))
            if (it.country?.isNotEmpty() == true) filters.add(CountryFilters(it.country))
        }
        filters.add(RatingRangeFilter())
        filters.add(ChaptersRangeFilter())
        filters.add(YearRangeFilter())
        return FilterList(filters)
    }

    companion object {
        private val chapterNumberRegex = Regex("""Глава\s*([\d.]+)""")
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    }
}
