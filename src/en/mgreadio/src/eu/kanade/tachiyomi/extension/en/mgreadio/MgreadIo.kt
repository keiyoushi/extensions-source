package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class MgreadIo : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = mangasPageFromHtml(
        client.get(pageUrl("manga-ranking", page)).asJsoup(),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangasPageFromHtml(
        client.get(pageUrl("recently-updated", page)).asJsoup(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query.trim())
                .addQueryParameter("page", page.toString())
                .build()
            return MangasPage(
                client.get(url).parseAs<List<MgreadSearchDto>>().mapNotNull(::mangaFromSearchDto),
                false,
            ).withoutAnimeEntries()
        }

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("advanced-filter")
            .apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addPathSegment("")
                addFilters(filters)
            }
            .build()

        val document = client.get(url).asJsoup()
        val mangas = document.select(".manga-item-grid, .manga-item-details")
            .map(::mangaFromGridElement)
            .distinctBy { it.url }
        return MangasPage(mangas, document.selectFirst("li:not(.uk-disabled) > a[aria-label='Next page']") != null)
            .withoutAnimeEntries()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/manga/")) return null
        return mangaDetailsFromHtml(client.get(url).asJsoup())
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select(".manga-block:has(> h2:contains(Related Manga)) .manga-item-slider")
        .map(::mangaFromRelatedElement)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.memo[MANGA_ID_MEMO]?.int
        if (mangaId == null) {
            val document = client.get(getMangaUrl(manga)).asJsoup()
            val updatedManga = mangaDetailsFromHtml(document)
            return@coroutineScope SMangaUpdate(
                updatedManga,
                if (fetchChapters) fetchChapterList(updatedManga.mangaId(), manga.url) else chapters,
            )
        }

        val mangaDeferred = async {
            if (fetchDetails) mangaDetailsFromHtml(client.get(getMangaUrl(manga)).asJsoup()) else manga
        }
        val chaptersDeferred = async {
            if (fetchChapters) fetchChapterList(mangaId, manga.url) else chapters
        }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private fun mangaDetailsFromHtml(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("#manga-title")?.ownText()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" [Ch.")?.trim()
            ?: error("Title not found")

        thumbnail_url = document.selectFirst(".story-cover img, meta[property=og:image]")?.imageUrl()

        val descriptionText = document.selectFirst("#manga-description")?.wholeText()?.trim()
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        genre = document.select("#genre-tags a[href*='/genre/']")
            .joinToString { it.ownText().ifEmpty { it.text() } }

        status = document.selectFirst("#manga-status")?.text().parseStatus()

        val metaRow = document.selectFirst("#manga-title + div")
        val metadata = buildList {
            metaRow?.ownText()
                ?.substringBefore("Chapters")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { add("Chapters: $it") }

            document.selectFirst("#comic-othername")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let { add("Alternative title: $it") }

            document.selectFirst(".init-review-info")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let { add("Rating: $it") }

            metaRow?.selectFirst(".init-plugin-suite-view-count-number")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let { add("Views: $it") }

            document.selectFirst("#last-updated")?.text()
                ?.takeIf(String::isNotEmpty)
                ?.let { add("Last updated: $it") }
        }

        description = buildString {
            if (descriptionText != null) append(descriptionText)
            if (metadata.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                metadata.joinTo(this, separator = "\n")
            }
        }

        url = document.location().toHttpUrl().encodedPath
        memo = buildJsonObject { put(MANGA_ID_MEMO, document.mangaId()) }
    }

    private suspend fun fetchChapterList(mangaId: Int, mangaPath: String): List<SChapter> = coroutineScope {
        val firstPage = fetchChapterPage(mangaId, 1)
        val remainingPages = (2..firstPage.totalPages).map { page ->
            async { fetchChapterPage(mangaId, page) }
        }.awaitAll()

        (listOf(firstPage) + remainingPages).flatMap { page ->
            page.items.map { it.toSChapter(mangaPath) }
        }
    }

    private suspend fun fetchChapterPage(mangaId: Int, page: Int): ChapterListDto {
        val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId.toString())
            .addQueryParameter("paged", page.toString())
            .addQueryParameter("per_page", "50")
            .build()
        return client.get(url).parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterUrl = document.location()
        return document.select("#chapter-content img[data-original-src]").mapIndexed { index, element ->
            Page(index, url = chapterUrl, imageUrl = element.absUrl("data-original-src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/advanced-filter/").asJsoup()
        .select("input[name='genre[]'][data-genre-name][value]")
        .map { GenreOption(it.attr("data-genre-name"), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf(
            Filter.Header("Filters are only applied when the search text is empty."),
            TypeFilter(),
            StatusFilter(),
            AgeRatingFilter(),
            RatingMinFilter(),
            RatingMaxFilter(),
            SortFilter(),
        )
        data?.parseAs<List<GenreOption>>()?.takeIf { it.isNotEmpty() }?.let {
            filters += GenreFilter(it)
        }
        return FilterList(filters)
    }

    // Helpers

    private fun pageUrl(slug: String, page: Int): String = if (page == 1) {
        "$baseUrl/$slug/"
    } else {
        "$baseUrl/$slug/page/$page/"
    }

    private fun mangasPageFromHtml(document: Document): MangasPage {
        val mangas = document.select(".manga-item-grid").map(::mangaFromGridElement)
        val hasNextPage = document.selectFirst("li:not(.uk-disabled) > a[aria-label='Next page']") != null
        return MangasPage(mangas, hasNextPage).withoutAnimeEntries()
    }

    private fun mangaFromGridElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("h2 a[href*='/manga/']")
            ?: element.selectFirst("a[href*='/manga/']:not([href*='/chapter-'])")
            ?: error("Manga link not found in grid item")

        title = titleElement.text()
        url = titleElement.absUrl("href").toHttpUrl().encodedPath
        thumbnail_url = element.selectFirst("img")?.imageUrl()
    }

    private fun mangaFromRelatedElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[href*='/manga/']") ?: error("Related manga link not found")
        title = element.selectFirst("h3")?.text() ?: error("Related manga title not found")
        url = link.absUrl("href").toHttpUrl().encodedPath
        thumbnail_url = element.selectFirst("img")?.imageUrl()
    }

    private fun mangaFromSearchDto(dto: MgreadSearchDto): SManga? {
        val parsedTitle = Jsoup.parse(dto.title).text()
        val cleanUrl = dto.url.trim().takeIf(String::isNotEmpty) ?: return null

        return SManga.create().apply {
            title = parsedTitle
            thumbnail_url = dto.thumb
            url = cleanUrl.toHttpUrl().encodedPath
            memo = buildJsonObject { put(MANGA_ID_MEMO, dto.id) }
        }
    }

    private fun SManga.isAnimeEntry(): Boolean {
        val normalizedTitle = title.lowercase()
        return normalizedTitle.startsWith("anime -") ||
            normalizedTitle.startsWith("anime –") ||
            url.substringAfter("/manga/", "").startsWith("anime-")
    }

    private fun MangasPage.withoutAnimeEntries(): MangasPage = MangasPage(
        mangas.filterNot { it.isAnimeEntry() },
        hasNextPage,
    )

    private fun Element.imageUrl(): String? = when (normalName()) {
        "meta" -> attr("content")
        else -> attr("abs:data-src").ifEmpty {
            attr("abs:data-lazy-src").ifEmpty { attr("abs:src") }
        }
    }.takeIf(String::isNotEmpty)

    private fun String?.parseStatus(): Int = when (this?.lowercase(Locale.US)?.trim()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "season end", "source hiatus", "caught up" -> SManga.ON_HIATUS
        "dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Document.mangaId(): Int = selectFirst("#manga-title[data-id], #chapter-search-input[data-manga-id]")
        ?.let { element -> element.attr("data-id").ifEmpty { element.attr("data-manga-id") } }
        ?.toIntOrNull()
        ?: error("Manga ID not found")

    private fun SManga.mangaId(): Int = memo[MANGA_ID_MEMO]?.int ?: error("Manga ID not found")

    companion object {
        private const val MANGA_ID_MEMO = "mangaId"
    }
}
