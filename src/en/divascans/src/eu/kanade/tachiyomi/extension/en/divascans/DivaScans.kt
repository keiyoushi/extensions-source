package eu.kanade.tachiyomi.extension.en.divascans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DivaScans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("Referer", "$baseUrl/")

    // --- Configuration (Settings Menu) ---

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val paidChapterPref = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_CHAPTERS_PREF
            title = "Show Paid Chapters"
            summary = "Show chapters that require coins to read. Note: You cannot read these natively in the app."
            setDefaultValue(false)
        }
        screen.addPreference(paidChapterPref)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        OriginFilter(),
        GenreFilter(),
        TagFilter(),
    )

    // --- Direct API Catalog & Search Layer ---

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        for (filter in filters) {
            when (filter) {
                is SortFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("sort", filter.selected)
                is StatusFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("status", filter.selected)
                is OriginFilter -> if (filter.selected.isNotEmpty()) url.addQueryParameter("origin", filter.selected)
                is GenreFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("genre", it.value) }
                is TagFilter -> filter.state.filter { it.state }.forEach { url.addQueryParameter("tag", it.value) }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseJsonMangaList(response)
    override fun searchMangaParse(response: Response): MangasPage = parseJsonMangaList(response)

    private fun parseJsonMangaList(response: Response): MangasPage {
        val bodyString = response.body.string()

        // The API sometimes returns a bare array, and sometimes an object with the
        // list nested under one of a few possible keys depending on the endpoint.
        val envelope = runCatching { bodyString.parseAs<List<MangaDto>>() }
            .map { SeriesResponse(data = it) }
            .getOrNull()
            ?: runCatching { bodyString.parseAs<SeriesResponse>() }.getOrNull()
            ?: return MangasPage(emptyList(), false)

        val items = envelope.mangaList ?: return MangasPage(emptyList(), false)

        val savedGenres = preferences.getStringSet(GENRES_PREF_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val savedTags = preferences.getStringSet(TAGS_PREF_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        var prefsChanged = false

        val mangas = items.mapNotNull { item ->
            val genresArr = item.genres
            if (genresArr != null) {
                for (genreObj in genresArr) {
                    val genreSlug = genreObj.genre?.slug ?: genreObj.slug
                    if (!genreSlug.isNullOrEmpty() && savedGenres.add(formatSlug(genreSlug))) prefsChanged = true
                }
            }

            val tagsArr = item.tags
            if (tagsArr != null) {
                for (tagObj in tagsArr) {
                    val tagSlug = tagObj.tag?.slug ?: tagObj.slug
                    if (!tagSlug.isNullOrEmpty() && savedTags.add(formatSlug(tagSlug))) prefsChanged = true
                }
            }

            val title = item.title?.takeIf { it.isNotBlank() } ?: item.name ?: ""
            val slug = item.urlSlug?.takeIf { it.isNotBlank() } ?: item.slug ?: ""

            val cover = item.coverImage?.takeIf { it.isNotBlank() } ?: item.thumbnail ?: ""

            if (title.isEmpty() || slug.isEmpty()) return@mapNotNull null

            val type = item.type?.lowercase() ?: item.category?.lowercase() ?: "comic"
            val urlType = if (type.contains("novel")) "novel" else "comic"

            SManga.create().apply {
                this.title = title
                this.url = "/series/$urlType/$slug"
                this.thumbnail_url = cleanImageUrl(cover)
            }
        }

        if (prefsChanged) {
            preferences.edit()
                .putStringSet(GENRES_PREF_KEY, savedGenres)
                .putStringSet(TAGS_PREF_KEY, savedTags)
                .apply()
        }

        val pageCount = envelope.meta?.pagination?.pageCount ?: envelope.totalPages
        val currentPage = envelope.meta?.pagination?.page
            ?: response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        val hasNextPage = if (pageCount != null) currentPage < pageCount else mangas.size >= 10

        return MangasPage(mangas, hasNextPage)
    }

    private fun formatSlug(slug: String): String = slug.replace("-", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    // --- HTML Details Layer ---

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.body.string()

        val doc = Jsoup.parse(html)
        val manga = SManga.create()

        // 1. Next.js RSC extraction
        val item = extractSeriesData(doc)?.series
        if (item != null) {
            manga.title = item.title ?: ""
            manga.thumbnail_url = cleanImageUrl(item.coverImage ?: "")
            manga.description = item.description?.let {
                val cleaned = it
                    .replace("\\n", "\n")
                    .replace("<br/>", "[[n]]")
                    .replace("<br />", "[[n]]")
                    .replace("<br>", "[[n]]")
                    .replace("<p>", "[[n]]")
                Jsoup.parseBodyFragment(cleaned).text().replace("[[n]]", "\n").trim()
            }
            manga.author = item.author
            manga.artist = item.artist ?: manga.author

            val statusStr = item.status ?: ""
            manga.status = when {
                statusStr.contains("Ongoing", true) -> SManga.ONGOING
                statusStr.contains("Completed", true) -> SManga.COMPLETED
                statusStr.contains("Hiatus", true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val origin = item.origin?.let { formatSlug(it) }
            val genresList = item.genres?.mapNotNull {
                it.genre?.name ?: it.name
            }
            val tagsList = item.tags?.mapNotNull {
                it.tag?.name ?: it.name
            }

            manga.genre = listOfNotNull(origin).plus(genresList ?: emptyList()).plus(tagsList ?: emptyList()).joinToString()

            if (!manga.description.isNullOrBlank()) return manga
        }

        // 2. DOM Fallback
        val domTitle = doc.selectFirst("h1")?.text()?.ifEmpty { doc.title() } ?: doc.title()
        if (manga.title.isBlank() || manga.title.contains("JavaScript Required", true)) {
            manga.title = if (domTitle.contains("JavaScript Required", true)) "" else domTitle
        }

        if (manga.thumbnail_url.isNullOrBlank()) {
            manga.thumbnail_url = cleanImageUrl(doc.selectFirst("img[src*='cover'], img[src*='thumbnail']")?.absUrl("src") ?: "")
        }

        if (manga.description.isNullOrBlank()) {
            val synopsisEl = doc.selectFirst("div:containsOwn(Synopsis), div:containsOwn(Description), div:containsOwn(synopsis)")?.nextElementSibling()
            manga.description = if (synopsisEl != null) {
                val cleaned = synopsisEl.html()
                    .replace("\\n", "\n")
                    .replace("<br/>", "[[n]]")
                    .replace("<br />", "[[n]]")
                    .replace("<br>", "[[n]]")
                    .replace("<p>", "[[n]]")
                Jsoup.parseBodyFragment(cleaned).text().replace("[[n]]", "\n").trim()
            } else {
                doc.selectFirst("main p")?.text()
            }
        }

        if (manga.author.isNullOrBlank()) {
            manga.author = doc.selectFirst("span:containsOwn(Author)")?.nextElementSibling()?.text()
                ?: doc.selectFirst("a[href*='/team/']")?.text()
        }

        if (manga.status == SManga.UNKNOWN) {
            val statusText = doc.selectFirst("span:containsOwn(Status)")?.nextElementSibling()?.text()
                ?: doc.selectFirst("div:containsOwn(Status) + div")?.text() ?: ""
            manga.status = when {
                statusText.contains("Ongoing", true) -> SManga.ONGOING
                statusText.contains("Completed", true) -> SManga.COMPLETED
                statusText.contains("Hiatus", true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }

        if (manga.genre.isNullOrBlank()) {
            val originText = doc.selectFirst("span:containsOwn(Origin)")?.nextElementSibling()?.text()
                ?: doc.selectFirst("div:containsOwn(Origin) + a")?.text() ?: ""
            val genres = doc.select("a[href*='genre=']").map { it.text() }
            val tags = doc.select("a[href*='tag=']").map { it.text() }
            manga.genre = listOfNotNull(originText.takeIf { it.isNotBlank() }).plus(genres).plus(tags).joinToString()
        }

        return manga
    }

    // --- HTML Chapter Layer ---

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()

        val showPaid = preferences.getBoolean(SHOW_PAID_CHAPTERS_PREF, false)
        val slug = response.request.url.pathSegments.last()

        val doc = Jsoup.parse(html)

        // 1. Next.js RSC extraction
        val chaptersArray = extractSeriesData(doc)?.let { it.chapters ?: it.series?.chapters }
        if (!chaptersArray.isNullOrEmpty()) {
            val chapters = chaptersArray.mapNotNull { chap ->
                val isLocked = (chap.isLocked ?: false) || (chap.coinPrice ?: 0) > 0
                val coinPrice = chap.coinPrice ?: 0

                if (isLocked && !showPaid) return@mapNotNull null

                val numStr = chap.number?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                val type = response.request.url.pathSegments.let {
                    val rawType = if (it.size >= 2) it[it.size - 2] else "comic"
                    if (rawType.contains("novel")) "novel" else "comic"
                }

                SChapter.create().apply {
                    // Use number in URL as requested by site structure
                    url = "/series/$type/$slug/chapter/$numStr"

                    val prefix = if (isLocked) "\uD83D\uDD12 " else ""
                    val suffix = if (isLocked && coinPrice > 0) " ($coinPrice coins)" else ""
                    val title = chap.title?.takeIf { it != "null" && it.isNotBlank() }
                    val baseName = when {
                        title == null -> "Chapter $numStr"
                        title.equals("Chapter $numStr", true) -> title
                        else -> "Chapter $numStr - $title"
                    }

                    name = "$prefix$baseName$suffix"
                    chapter_number = numStr.toFloatOrNull() ?: 0f
                    date_upload = dateFormat.tryParse(chap.publishedAt)
                }
            }
            if (chapters.size > 1) return chapters.sortedByDescending { it.chapter_number }
        }

        // 2. Fallback: Parse via DOM
        val chapterElements = doc.select("a[href*='/chapter/']")
        if (chapterElements.isNotEmpty()) {
            val chapters = chapterElements.mapNotNull { element ->
                val nameText = element.text()

                val href = element.attr("href")
                val chapterUrl = when {
                    href.startsWith("http") -> href.substringAfter(baseUrl)
                    href.startsWith("/") -> href
                    else -> return@mapNotNull null
                }

                if (!chapterUrl.contains("/chapter/")) return@mapNotNull null

                SChapter.create().apply {
                    url = chapterUrl
                    name = when {
                        nameText.contains("Start Reading", true) -> "Chapter 1"
                        nameText.contains("Continue Reading", true) -> "Continue Reading"
                        nameText.isBlank() -> "Chapter 1"
                        else -> nameText
                    }
                    chapter_number = nameText.substringAfter("Chapter").trim().takeWhile { it.isDigit() || it == '.' }.toFloatOrNull()
                        ?: nameText.filter { it.isDigit() || it == '.' }.toFloatOrNull()
                        ?: if (name.contains("Chapter 1", true)) 1f else 0f
                }
            }.distinctBy { it.url }

            if (chapters.isNotEmpty()) {
                // If we found regular chapters, filter out the navigation buttons
                val filtered = if (chapters.size > 1) {
                    chapters.filterNot { it.name.contains("Start Reading", true) || it.name.contains("Continue Reading", true) }
                } else {
                    chapters.map { chap ->
                        if (chap.name.contains("Start Reading", true)) chap.name = "Chapter 1"
                        chap
                    }
                }
                return filtered.sortedByDescending { it.chapter_number }
            }
        }

        return emptyList()
    }

    // --- Page Reader Layer ---

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val doc = Jsoup.parse(html)

        // 1. RSC extraction — find pages array in flight data
        val pages = runCatching {
            doc.extractNextJs<ChapterPageDataDto> { element ->
                element is JsonObject && "pages" in element
            }?.pages
        }.getOrNull()

        if (!pages.isNullOrEmpty()) {
            return pages.mapIndexed { index, page ->
                Page(index, "", cleanImageUrl(page.imageUrl ?: ""))
            }
        }

        // 2. Fallback: DOM images (server-rendered)
        val domImages = doc.select("div.reader-images img, div.chapter-container img, main img[src*='chapter']")
        if (domImages.isNotEmpty()) {
            return domImages.mapIndexed { index, element ->
                val imgUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
                Page(index, "", cleanImageUrl(imgUrl))
            }
        }

        return emptyList()
    }

    // --- Utilities ---

    private fun extractSeriesData(doc: org.jsoup.nodes.Document): SeriesPageDto? = runCatching {
        doc.extractNextJs<SeriesPageDto> { element ->
            element is JsonObject && "series" in element
        }
    }.getOrNull()

    private fun cleanImageUrl(url: String): String {
        if (url.isEmpty() || url.startsWith("data:")) return url

        val absoluteUrl = if (url.startsWith("/")) "$baseUrl$url" else url
        val httpUrl = absoluteUrl.toHttpUrlOrNull() ?: return url

        // 1. Decode proxy URLs
        var cleanUrl = httpUrl.queryParameter("url") ?: absoluteUrl

        // 2. Ensure base URL
        if (cleanUrl.startsWith("/")) {
            cleanUrl = "$baseUrl$cleanUrl"
        }

        // 3. Force CDN and strip invalid next/image paths
        return cleanUrl.replace("divascans.org", "media.divascans.org")
            .replace("media.media.divascans.org", "media.divascans.org")
            .replace("/_next/image", "")
            .replace("/uploads/", "/")
            .substringBefore("?") // Strip all Next.js parameters
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val SHOW_PAID_CHAPTERS_PREF = "show_paid_chapters"
        private const val GENRES_PREF_KEY = "saved_genres"
        private const val TAGS_PREF_KEY = "saved_tags"
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }
}
