package eu.kanade.tachiyomi.multisrc.zinmangaart

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

enum class UrlLayout {
    MANGA_SLUG_CHAPTER_SLUG,
    LEGACY_MANGA_PATH_CHAPTER_PATH,
    LEGACY_MANGA_PATH_CHAPTER_SLUG,
}

@Source
abstract class ZinMangaArt : KeiSource() {
    protected open val searchPath: String = "/"
    protected open val urlLayout: UrlLayout = UrlLayout.MANGA_SLUG_CHAPTER_SLUG

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter.POPULAR))
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter.LATEST))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val orderBy = (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).toUriPart()
        val genres = (filters.firstInstanceOrNull<GenreFilter>() ?: GenreFilter(emptyList())).selectedGenres()
        val genreOperator = (filters.firstInstanceOrNull<GenreOperatorFilter>() ?: GenreOperatorFilter()).toUriPart()
        val author = (filters.firstInstanceOrNull<AuthorFilter>() ?: AuthorFilter()).state
        val artist = (filters.firstInstanceOrNull<ArtistFilter>() ?: ArtistFilter()).state
        val release = (filters.firstInstanceOrNull<ReleaseFilter>() ?: ReleaseFilter()).state
        val adult = (filters.firstInstanceOrNull<AdultFilter>() ?: AdultFilter()).toUriPart()
        val statuses = (filters.firstInstanceOrNull<StatusFilter>() ?: StatusFilter()).selectedStatuses()

        val url = "$baseUrl$searchPath".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page")
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
            genres.forEach { addQueryParameter("genre[]", it) }
            addQueryParameter("op", genreOperator)
            addQueryParameter("author", author)
            addQueryParameter("artist", artist)
            addQueryParameter("release", release)
            addQueryParameter("adult", adult)
            statuses.forEach { addQueryParameter("status[]", it.value) }
            addQueryParameter("orderby", orderBy)
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document
            .select("div.page-item-detail, div.c-tabs-item__content")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst("a[aria-label=Next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val link = element.selectFirst(".post-title a, .widget-title a")!!
        val slug = link.absUrl("href")
            .toHttpUrlOrNull()!!
            .pathSegments[1]
        val image = element.selectFirst("img")

        return SManga.create().apply {
            url = when (urlLayout) {
                UrlLayout.MANGA_SLUG_CHAPTER_SLUG -> slug
                UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH, UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG -> "/manga/$slug"
            }
            title = link.text()
            thumbnail_url = image?.absUrl("data-backup")
                ?.ifBlank { image.absUrl("data-src") }
                ?.ifBlank { image.absUrl("src") }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = when (urlLayout) {
                UrlLayout.MANGA_SLUG_CHAPTER_SLUG -> slug
                UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH, UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG -> "/manga/$slug"
            }
        }

        return fetchMangaDetails(manga).apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = when (urlLayout) {
        UrlLayout.MANGA_SLUG_CHAPTER_SLUG -> "$baseUrl/manga/${manga.url}"
        UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH, UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG -> super.getMangaUrl(manga)
    }

    override fun getChapterUrl(chapter: SChapter): String = when (urlLayout) {
        UrlLayout.MANGA_SLUG_CHAPTER_SLUG -> {
            val mangaSlug = chapter.memo["mangaSlug"]!!.string
            "$baseUrl/manga/$mangaSlug/${chapter.url}"
        }
        UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG -> {
            // Legacy: mirrors MadaraBase.getChapterUrl().
            if (chapter.url.contains('/')) error("Refresh the chapter list.")
            val mangaPath = chapter.memo.getStringOrNull("mangaPath")
                ?: error("Refresh the chapter list.")
            "$baseUrl${mangaPath.trimEnd('/')}/${chapter.url}/"
        }
        UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH -> super.getChapterUrl(chapter)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapters(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga = parseDetails(
        manga,
        client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup(),
    )

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        val authors = document.select("script[type=application/ld+json]")
            .mapNotNull { runCatching { it.data().parseAs<MangaJsonLd>() }.getOrNull() }
            .firstOrNull { it.type == "Book" }
            ?.author
            ?.mapNotNull { it.name }

        title = document.selectFirst("div.post-title h1")!!.text()

        thumbnail_url = document.selectFirst(".summary_image img")?.absUrl("src")
        author = authors?.joinToString { Parser.unescapeEntities(it, false) }?.ifEmpty { null }
        memo = buildJsonObject {
            put(
                "relatedMangas",
                document.select(".related-manga .related-reading-wrap")
                    .map(::mangaFromElement)
                    .map(SManga::toRelatedManga)
                    .toJsonElement(),
            )
        }
        genre = document.select(".tab-summary .genres-content a")
            .joinToString { it.text() }
            .ifEmpty { null }

        status = when (document.selectFirst(".post-status .post-content_item:has(.summary-heading h5:containsOwn(Status)) .summary-content")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "drop" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val mainPlot = document.selectFirst(".description-summary .summary__content h3:containsOwn(Main Plot)")
            ?.nextElementSibling()
            ?.takeUnless { it.text() in setOf("No synopsis available", "N/A") }
        val viewCount = document.selectFirst(".post-content_item:has(.summary-heading h5:containsOwn(View)) .summary-content")
            ?.textOrNull()
        val rating = document.selectFirst("#averagerate")?.text()?.toDoubleOrNull()
        val ratingVotes = document.selectFirst("#countrate")?.text()?.toIntOrNull()
        val altTitles = document.selectFirst(".tab-summary .post-content_item:has(.summary-heading h5:containsOwn(Alternative)) .summary-content")
            ?.textOrNull()
            ?.split(',', ';', '/') // best effort
            ?.map(String::trim)
            .orEmpty()
        val release = document.selectFirst(".post-status .post-content_item:has(.summary-heading h5:containsOwn(Release)) .summary-content")
            ?.textOrNull()
        description = MarkdownDescription.Builder()
            .rating(rating, max = 5, votes = ratingVotes)
            .description(mainPlot)
            .field("View", viewCount)
            .field("Release", release)
            .altTitles(altTitles)
            .build()
    }

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        val slug = when (urlLayout) {
            UrlLayout.MANGA_SLUG_CHAPTER_SLUG -> manga.url
            UrlLayout.LEGACY_MANGA_PATH_CHAPTER_PATH, UrlLayout.LEGACY_MANGA_PATH_CHAPTER_SLUG ->
                // Legacy: Madara-style slug extraction from stored manga path.
                manga.url.trimEnd('/').substringAfterLast('/')
        }
        val url = "$baseUrl/api/comics/$slug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "100")
            .addQueryParameter("order", "desc")
            .build()

        suspend fun fetchPage(page: Int) = client.get(
            url.newBuilder().addQueryParameter("page", page.toString()).build(),
            Fetch.XHR,
        ).parseAs<ChapterListResponse>().data

        val first = fetchPage(1)
        val rest = coroutineScope {
            (2..first.lastPage).map { async { fetchPage(it).chapters } }.awaitAll()
        }

        return (first.chapters + rest.flatten()).map { it.toSChapter(slug, urlLayout) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".reading-content .page-break img").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl$searchPath", Fetch.DOCUMENT).asJsoup()
        .select(".menu-item-object-wp-manga-genre a")
        .map { it.text() to it.absUrl("href").substringAfterLast('/') }
        .toJsonElement()

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = manga.memo["relatedMangas"]
        ?.parseAs<List<RelatedManga>>()
        ?.map(RelatedManga::toSManga)
        .orEmpty()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())

            val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty()
            if (genres.isNotEmpty()) {
                add(Filter.Separator())
                add(GenreFilter(genres))
                add(GenreOperatorFilter())
                add(Filter.Separator())
            }

            add(AuthorFilter())
            add(ArtistFilter())
            add(ReleaseFilter())
            add(AdultFilter())
            add(StatusFilter())
        },
    )
}
