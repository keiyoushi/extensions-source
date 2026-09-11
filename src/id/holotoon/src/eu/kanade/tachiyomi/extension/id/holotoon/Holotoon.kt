package eu.kanade.tachiyomi.extension.id.holotoon

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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Base64
import java.util.Calendar

@Source
abstract class Holotoon : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage =
        getBrowsePage(page = page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        getBrowsePage(page = page, sort = "latest")

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.value ?: "latest"
        val type = filters.filterIsInstance<TypeFilter>().firstOrNull()?.value.orEmpty()
        val status = filters.filterIsInstance<StatusFilter>().firstOrNull()?.value.orEmpty()
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.value.orEmpty()

        return getBrowsePage(
            page = page,
            sort = sort,
            query = query,
            type = type,
            status = status,
            genre = genre,
        )
    }

    private suspend fun getBrowsePage(
        page: Int,
        sort: String,
        query: String = "",
        type: String = "",
        status: String = "",
        genre: String = "",
    ): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("browse")
            addQueryParameter("sort", sort)
            addQueryParameter("media", "comic")
            if (query.isNotBlank()) addQueryParameter("q", query)
            if (type.isNotBlank()) addQueryParameter("type", type)
            if (status.isNotBlank()) addQueryParameter("status", status)
            if (genre.isNotBlank()) addQueryParameter("genre", genre)
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return parseMangasPage(client.get(url).asJsoup(), page)
    }

    private fun parseMangasPage(document: Document, page: Int): MangasPage {
        // The site contains scraper honeypots. Restrict parsing to the real catalogue grid.
        val container = document.select("div.grid:has(a[href^='/comic/'])").lastOrNull()
            ?: return MangasPage(emptyList(), false)
        val seen = hashSetOf<String>()

        val mangas = container.select("a.group[href^='/comic/']").mapNotNull { card ->
            val path = card.attr("href").substringBefore('#').substringBefore('?')
            val title = card.selectFirst("h3")?.text()?.trim().orEmpty()
            if (path.isBlank() || title.isBlank() || !seen.add(path)) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(path)
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.takeIf(String::isNotBlank)
            }
        }

        val hasNextPage = document.select("a[href*='page=']")
            .any { it.attr("href").contains("page=${page + 1}") }

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = normalizeMangaPath(manga.url)
        val document = client.get("$baseUrl$path").asJsoup()

        return SMangaUpdate(
            manga = if (fetchDetails) parseDetails(document, path) else manga,
            chapters = if (fetchChapters) parseChapters(document) else chapters,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${normalizeMangaPath(manga.url)}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${normalizeChapterPath(chapter.url)}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = normalizeMangaPath(url.encodedPath)
        if (!path.startsWith("/comic/")) return null
        return parseDetails(client.get("$baseUrl$path").asJsoup(), path)
    }

    private fun parseDetails(document: Document, path: String): SManga {
        val titleElement = document.selectFirst("h1")
        val title = titleElement?.text()?.trim().orEmpty()
        val detailsRoot = findDetailsRoot(titleElement)
        val metadata = detailsRoot?.text().orEmpty()

        val descriptionElement = document.selectFirst(
            "#synopsis-wrapper div[data-sr], div[data-sr][class*=synopsis], div.prose, div[class*=description]",
        )
        val description = descriptionElement?.attr("data-sr")
            ?.takeIf(String::isNotBlank)
            ?.let { encoded ->
                runCatching {
                    String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
                }.getOrNull()
            }
            ?: descriptionElement?.text()?.trim()?.takeIf(String::isNotBlank)

        val genreElements = detailsRoot?.select("a[href*='genre=']").orEmpty()

        return SManga.create().apply {
            setUrlWithoutDomain(path)
            this.title = title
            author = AUTHOR_REGEX.find(metadata)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
            artist = ARTIST_REGEX.find(metadata)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
            genre = genreElements.map { it.text().trim() }.filter(String::isNotBlank).distinct().joinToString()
            this.description = description
            status = parseStatus(detailsRoot ?: document)
            thumbnail_url = document.select("img")
                .firstOrNull { it.attr("alt").trim().equals(title, ignoreCase = true) }
                ?.absUrl("src")
                ?.takeIf(String::isNotBlank)
        }
    }

    private fun findDetailsRoot(titleElement: Element?): Element? {
        var element = titleElement?.parent()
        repeat(6) {
            if (element == null) return null
            if (element!!.select("a[href*='genre=']").isNotEmpty()) return element
            element = element!!.parent()
        }
        return titleElement?.parent()
    }

    private fun parseStatus(root: Element): Int {
        val value = root.select("span")
            .asSequence()
            .map { it.text().trim().lowercase() }
            .firstOrNull { it in STATUS_VALUES }

        return when (value) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> =
        document.select("a[href^='/read/'][data-chapter]").mapNotNull { element ->
            val path = element.attr("href").substringBefore('#').substringBefore('?')
            if (path.isBlank()) return@mapNotNull null

            val rawChapter = element.attr("data-chapter")
            val number = CHAPTER_NUMBER_REGEX.find(rawChapter)?.value?.toFloatOrNull()
            val label = element.selectFirst("span.font-semibold")?.text()?.trim()
            val subtitle = element.selectFirst("span.truncate")?.text()?.trim()
                ?.removePrefix("—")
                ?.trim()
                ?.takeIf(String::isNotBlank)

            SChapter.create().apply {
                setUrlWithoutDomain(path)
                name = when {
                    !label.isNullOrBlank() && subtitle != null -> "$label - $subtitle"
                    !label.isNullOrBlank() -> label
                    subtitle != null -> subtitle
                    number != null -> "Chapter ${number.toString().removeSuffix(".0")}"
                    else -> rawChapter.ifBlank { "Chapter" }
                }
                chapter_number = number ?: -1f
                date_upload = parseRelativeDate(
                    element.selectFirst("span.text-right, span[class*=tabular-nums]:last-child")?.text(),
                )
            }
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val readerImages = document.select("#reader-pages img")
        val images = if (readerImages.isNotEmpty()) {
            readerImages
        } else {
            document.select("main img[src*='/image/comic/'], main img[data-src*='/image/comic/']")
        }
        val seen = hashSetOf<String>()

        return images.mapNotNull { image ->
            val imageUrl = image.absUrl("src").ifBlank { image.absUrl("data-src") }
            if (
                imageUrl.isBlank() ||
                imageUrl.contains("/chapter-header/") ||
                imageUrl.contains("/chapter-footer/") ||
                !seen.add(imageUrl)
            ) {
                return@mapNotNull null
            }

            Page(index = seen.size - 1, imageUrl = imageUrl)
        }
    }

    private fun normalizeMangaPath(url: String): String {
        val path = url.toHttpUrlOrNull()?.encodedPath ?: url.substringBefore('?').substringBefore('#')
        val withLeadingSlash = if (path.startsWith('/')) path else "/$path"
        return when {
            withLeadingSlash.startsWith("/komik/") -> withLeadingSlash.replaceFirst("/komik/", "/comic/")
            else -> withLeadingSlash
        }.trimEnd('/')
    }

    private fun normalizeChapterPath(url: String): String {
        val path = url.toHttpUrlOrNull()?.encodedPath ?: url.substringBefore('?').substringBefore('#')
        val withLeadingSlash = if (path.startsWith('/')) path else "/$path"
        if (!withLeadingSlash.startsWith("/komik/")) return withLeadingSlash.trimEnd('/')

        val parts = withLeadingSlash.trim('/').split('/')
        return if (parts.size >= 3) {
            "/read/${parts[1]}/${parts.drop(2).joinToString("/")}".trimEnd('/')
        } else {
            withLeadingSlash.trimEnd('/')
        }
    }

    private fun parseRelativeDate(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val normalized = text.trim().lowercase()
        if (normalized == "baru saja" || normalized == "just now") return System.currentTimeMillis()

        val amount = NUMBER_REGEX.find(normalized)?.value?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()

        when {
            "detik" in normalized || "second" in normalized -> calendar.add(Calendar.SECOND, -amount)
            "menit" in normalized || "minute" in normalized -> calendar.add(Calendar.MINUTE, -amount)
            "jam" in normalized || "hour" in normalized -> calendar.add(Calendar.HOUR, -amount)
            "hari" in normalized || "day" in normalized -> calendar.add(Calendar.DAY_OF_MONTH, -amount)
            "minggu" in normalized || "week" in normalized -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
            "bulan" in normalized || "month" in normalized -> calendar.add(Calendar.MONTH, -amount)
            "tahun" in normalized || "year" in normalized -> calendar.add(Calendar.YEAR, -amount)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/browse").asJsoup()
        return buildJsonArray {
            document.select("select[name=genre] option").forEach { option ->
                val value = option.attr("value").trim()
                val name = option.text().trim()
                if (value.isNotBlank() && name.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("name", name)
                            put("value", value)
                        },
                    )
                }
            }
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonArray.orEmpty().mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            name to value
        }

        return FilterList(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            GenreFilter(genres),
        )
    }

    private class SortFilter : Filter.Select<String>(
        "Urutkan",
        arrayOf("Terbaru", "Populer", "Rating", "A-Z"),
    ) {
        val value get() = arrayOf("latest", "popular", "rating", "az")[state]
    }

    private class TypeFilter : Filter.Select<String>(
        "Tipe",
        arrayOf("Semua", "Manga", "Manhwa", "Manhua", "Comic", "Webtoon"),
    ) {
        val value get() = arrayOf("", "manga", "manhwa", "manhua", "comic", "webtoon")[state]
    }

    private class StatusFilter : Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Ongoing", "Completed", "Hiatus"),
    ) {
        val value get() = arrayOf("", "ongoing", "completed", "hiatus")[state]
    }

    private class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>(
        "Genre",
        (listOf("Semua") + genres.map { it.first }).toTypedArray(),
    ) {
        val value get() = if (state == 0) "" else genres[state - 1].second
    }

    companion object {
        private val AUTHOR_REGEX = Regex("Author:\\s*(.*?)\\s+Artist:", RegexOption.IGNORE_CASE)
        private val ARTIST_REGEX = Regex("Artist:\\s*(.*?)\\s+(?:Year:|Views:|Uploaded by:)", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("\\d+(?:\\.\\d+)?")
        private val NUMBER_REGEX = Regex("\\d+")
        private val STATUS_VALUES = setOf("ongoing", "completed", "hiatus", "dropped")
    }
}
