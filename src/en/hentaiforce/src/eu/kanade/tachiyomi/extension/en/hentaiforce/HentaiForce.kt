package eu.kanade.tachiyomi.extension.en.hentaiforce

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class HentaiForce : KeiSource() {

    override val supportsRelatedMangas = true

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }

    // The site has no overall popular list, so this uses the English one
    override suspend fun getPopularManga(page: Int): MangasPage = galleryList(listUrl(englishPopular, page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = galleryList(listUrl(emptyList(), page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addQueryParameter("q", query.trim())
                sort?.let { addQueryParameter("sort", it) }
                if (page > 1) addQueryParameter("page", page.toString())
            }.build()
            return galleryList(url)
        }
        val category = filters.firstInstance<CategoryFilter>().value
        val language = filters.firstInstance<LanguageFilter>().value
        val popular = sort == "popular"
        val segments = when {
            category != null -> listOfNotNull("category", category, "popular".takeIf { popular })
            language != null -> listOfNotNull("language", language, "popular".takeIf { popular })
            popular -> englishPopular
            else -> emptyList()
        }
        return galleryList(listUrl(segments, page))
    }

    private fun listUrl(segments: List<String>, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach(::addPathSegment)
        if (page > 1) {
            if (segments.isEmpty()) addPathSegment("page")
            addPathSegment(page.toString())
        }
    }.build()

    private suspend fun galleryList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".listing-galleries-container .gallery:has(.gallery-name a[href*=/view/])").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst(".pagination a[rel=next]") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".gallery-name a")!!
        url = link.absUrl("href").toHttpUrl().pathSegments[1]
        title = link.text()
        // Same image id as the details cover, which is the larger size
        thumbnail_url = selectFirst(".gallery-thumb img")?.attrOrNull("abs:data-src")?.replace("-thumb.", "-cover.")
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrlHost || segments.firstOrNull() !in listPaths || segments.size < 2) {
            return super.getMangasByUrl(url, page)
        }
        return galleryList(listUrl(segments.take(if (segments.getOrNull(2) == "popular") 3 else 2), page))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost || url.pathSegments.firstOrNull() != "view") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = id.toString() }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/view/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val info = document.selectFirst("#gallery-main-info")!!
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = parsePosted(info.field("Posted"))
        }
        return SMangaUpdate(mangaDetails(manga, document, info), listOf(chapter))
    }

    private fun mangaDetails(manga: SManga, document: Document, info: Element) = manga.apply {
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("#gallery-main-cover img")?.attrOrNull("abs:data-src")
        artist = info.links("Artists").joinToString().ifEmpty { null }
        author = artist
        genre = (info.links("Category") + info.links("Tags")).distinct().joinToString().ifEmpty { null }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = MarkdownDescription.Builder()
            .field("Parodies", info.links("Parodies"))
            .field("Characters", info.links("Characters"))
            .field("Languages", info.links("Languages"))
            .field("Pages", info.field("Pages"))
            .build()
    }

    private fun Element.links(label: String) = select(".tag-container:containsOwn($label:) a.tag-btn").map { it.ownText() }

    private fun Element.field(label: String) = selectFirst(".tag-container:containsOwn($label:)")
        ?.ownText()?.removePrefix("$label:")?.trim()

    private fun parsePosted(posted: String?): Long {
        val match = posted?.let(postedRegex::matchEntire) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unit = when (match.groupValues[2]) {
            "second" -> ChronoUnit.SECONDS
            "minute" -> ChronoUnit.MINUTES
            "hour" -> ChronoUnit.HOURS
            "day" -> ChronoUnit.DAYS
            "week" -> ChronoUnit.WEEKS
            "month" -> ChronoUnit.MONTHS
            else -> ChronoUnit.YEARS
        }
        return ZonedDateTime.now(ZoneOffset.UTC).minus(amount, unit).toInstant().toEpochMilli()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("#gallery-related .gallery:has(.gallery-name a[href*=/view/])")
        .map { it.toSManga() }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/view/${chapter.url}/1"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val encoded = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .select("script").firstNotNullOf { readerPagesRegex.find(it.data()) }.groupValues[1]
        return Base64.decode(encoded, Base64.DEFAULT).decodeToString().parseAs<ReaderDto>()
            .imageUrls()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Text search ignores Category and Language; Category wins over Language"),
        Filter.Header("Relevant applies to text search only"),
        SortFilter(),
        CategoryFilter(),
        LanguageFilter(),
    )

    companion object {
        private val englishPopular = listOf("language", "english", "popular")

        private val listPaths = setOf("tag", "artist", "parody", "character", "language", "category")

        private val postedRegex = Regex("""(\d+) (second|minute|hour|day|week|month|year)s? ago""")

        private val readerPagesRegex = Regex("""readerPages\s*=\s*JSON\.parse\(atob\("([^"]+)"""")
    }
}
