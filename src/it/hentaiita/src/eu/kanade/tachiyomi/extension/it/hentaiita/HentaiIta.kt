package eu.kanade.tachiyomi.extension.it.hentaiita

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Hentai ITA is a WordPress site; every list comes from its REST API, where the
 * categories work as tags (AND inclusion and exclusion are supported).
 *
 * The site exposes no popularity data (comments are closed, no view counter),
 * so "Popolari" is a random order, new every time the list is opened.
 */
@Source
abstract class HentaiIta : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override fun getHomeUrl(): String = "$baseUrl/it/"

    override suspend fun getPopularManga(page: Int): MangasPage = randomListing(page, Query())

    override suspend fun getLatestUpdates(page: Int): MangasPage = apiListing(page, Query(sort = SortMode.DATE))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val categories = filters.firstInstanceOrNull<CategoryGroup>()?.state.orEmpty()
        val search = Query(
            text = query.trim(),
            sort = sort?.mode ?: SortMode.RANDOM,
            ascending = sort?.ascending ?: false,
            section = filters.firstInstanceOrNull<SectionFilter>()?.section ?: SECTIONS[0],
            included = categories.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.id },
            excluded = categories.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.id },
        )
        // A tapped genre arrives as a plain query with no category filters: browse it.
        if (search.text.isNotEmpty() && categories.all { it.state == Filter.TriState.STATE_IGNORE }) {
            resolveCategory(search.text)?.let { id ->
                return apiListing(page, Query(sort = SortMode.DATE, section = SECTIONS[0], included = listOf(id)))
            }
        }
        return when (search.sort) {
            SortMode.RANDOM -> randomListing(page, search)
            SortMode.BEST -> bestThenLatest(page, search)
            else -> apiListing(page, search)
        }
    }

    // Resolves a category name (as shown on a chip) to its id.
    private suspend fun resolveCategory(name: String): Int? {
        val url = "$baseUrl/wp-json/wp/v2/categories".toHttpUrl().newBuilder()
            .addQueryParameter("search", name)
            .addQueryParameter("per_page", "20")
            .addQueryParameter("_fields", "id,slug,name")
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<TermDto>>()
            .firstOrNull { it.plainName.equals(name, ignoreCase = true) }?.id
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.lastOrNull { it.isNotEmpty() } ?: return null
        val post = fetchPost(slug) ?: return null
        return post.takeIf { isAllowed(it.classList, it.plainTitle) }?.toSManga()
    }

    // The REST API has no random order: shuffle the order of the result pages
    // and the entries inside each page. Opening the list again (page 1) draws a
    // new order; later pages keep it, so scrolling never repeats an entry.
    @Volatile
    private var randomSeed = 0L

    private suspend fun randomListing(page: Int, query: Query): MangasPage {
        if (page == 1) randomSeed = Random.nextLong()
        val seed = randomSeed
        val ordered = query.copy(sort = SortMode.DATE)
        val total = client.get(postsUrl(ordered, page = 1, perPage = 1), Fetch.XHR).use { response ->
            response.header("X-WP-Total")?.toIntOrNull() ?: 0
        }
        val totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE
        if (page > totalPages) return MangasPage(emptyList(), false)

        val sitePage = (1..totalPages).shuffled(Random(seed))[page - 1]
        val result = apiListing(sitePage, ordered)
        return MangasPage(result.mangas.shuffled(Random(seed + page)), page < totalPages)
    }

    // The "best" pages come first; once they run out, paging continues with
    // everything else (best excluded) so the list never repeats an entry.
    private suspend fun bestThenLatest(page: Int, query: Query): MangasPage {
        val best = query.copy(sort = SortMode.DATE, included = query.included + BEST_CATEGORY_ID)
        val bestTotal = client.get(postsUrl(best, page = 1, perPage = 1), Fetch.XHR).use { response ->
            response.header("X-WP-Total")?.toIntOrNull() ?: 0
        }
        val bestPages = (bestTotal + PAGE_SIZE - 1) / PAGE_SIZE
        if (page <= bestPages) {
            return MangasPage(apiListing(page, best).mangas, hasNextPage = true)
        }
        val rest = query.copy(sort = SortMode.DATE, excluded = query.excluded + BEST_CATEGORY_ID)
        return apiListing(page - bestPages, rest)
    }

    private suspend fun apiListing(page: Int, query: Query): MangasPage {
        val response = client.get(postsUrl(query, page, PAGE_SIZE), Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<PostDto>>()
            .filter { isAllowed(it.classList, it.plainTitle) }
            .map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    private fun postsUrl(query: Query, page: Int, perPage: Int): HttpUrl {
        val included = listOfNotNull(query.section.id) + query.included
        val excluded = BLOCKED_CATEGORIES.keys + NON_READING_CATEGORIES.keys + query.excluded
        val orderBy = when {
            query.sort == SortMode.RELEVANCE && query.text.isEmpty() -> "date"
            else -> query.sort.apiOrderBy
        }
        return "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", perPage.toString())
            if (included.isNotEmpty()) {
                addQueryParameter("categories[terms]", included.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
            addQueryParameter("categories_exclude", excluded.joinToString(","))
            if (query.text.isNotEmpty()) addQueryParameter("search", query.text)
            addQueryParameter("orderby", orderBy)
            addQueryParameter("order", if (query.ascending) "asc" else "desc")
            addQueryParameter("_embed", "wp:featuredmedia")
            addQueryParameter("_fields", LIST_FIELDS)
        }.build()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trim('/').substringAfterLast('/')
        val post = fetchPost(slug, details = true) ?: throw IOException("Titolo non trovato su Hentai ITA.")
        if (!isAllowed(post.classList, post.plainTitle)) {
            throw IOException("Questo titolo è escluso dall'estensione.")
        }
        val series = seriesChapters(post, manga.url)
        val pageDoc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val pageCount = galleryImages(pageDoc).ifEmpty { contentImages(pageDoc) }.count { PREMIUM_PATH !in it }

        manga.apply {
            title = post.plainTitle
            post.thumbnail?.let { thumbnail_url = it }
            genre = post.terms
                .filter { it.slug !in HIDDEN_GENRES }
                .joinToString { it.plainName }
            description = listOfNotNull(
                pageCount.takeIf { it > 0 }?.let { "Pagine: $it" },
                synopsis(post.contentText),
            ).joinToString("\n\n").ifEmpty { null }
            status = SManga.COMPLETED
            // A series gets new chapters; a one-shot never changes.
            update_strategy = if (series.isEmpty()) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        }

        val chapters = series.ifEmpty {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Capitolo unico"
                    chapter_number = 1f
                    date_upload = DATE_FORMAT.tryParse(post.dateGmt)
                },
            )
        }
        return SMangaUpdate(manga, chapters)
    }

    // The other chapters of the series: posts found by searching the base
    // title whose own title has the same base. Empty for a one-shot.
    private suspend fun seriesChapters(post: PostDto, mangaUrl: String): List<SChapter> {
        val base = SeriesTitle.split(post.plainTitle).base
        if (base.isEmpty()) return emptyList()
        val key = SeriesTitle.key(base)
        val searchUrl = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("search", base)
            .addQueryParameter("per_page", "100")
            .addQueryParameter("categories_exclude", (BLOCKED_CATEGORIES.keys + NON_READING_CATEGORIES.keys).joinToString(","))
            .addQueryParameter("_fields", "slug,link,title,date_gmt,class_list")
            .build()
        val found = client.get(searchUrl, Fetch.XHR).parseAs<List<PostDto>>()
            .filter { isAllowed(it.classList, it.plainTitle) }
            .mapNotNull { candidate ->
                val split = SeriesTitle.split(candidate.plainTitle)
                (candidate to (split.number ?: 1)).takeIf { SeriesTitle.key(split.base) == key }
            }
            .distinctBy { it.first.path }
        if (found.size < 2 || found.none { it.first.path == mangaUrl || it.first.slug == post.slug }) return emptyList()

        val duplicateNames = found.groupingBy { it.first.plainTitle }.eachCount().filterValues { it > 1 }.keys
        return found.sortedBy { it.second }.map { (chapterPost, number) ->
            SChapter.create().apply {
                url = chapterPost.path
                name = if (chapterPost.plainTitle in duplicateNames) "${chapterPost.plainTitle} – Capitolo $number" else chapterPost.plainTitle
                chapter_number = number.toFloat()
                date_upload = DATE_FORMAT.tryParse(chapterPost.dateGmt)
            }
        }.asReversed()
    }

    private suspend fun fetchPost(slug: String, details: Boolean = false): PostDto? {
        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_embed", if (details) "wp:featuredmedia,wp:term" else "wp:featuredmedia")
            .addQueryParameter("_fields", if (details) DETAIL_FIELDS else LIST_FIELDS)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<PostDto>>().firstOrNull()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT, CacheControl.FORCE_NETWORK).asJsoup()
        val images = galleryImages(doc).ifEmpty { contentImages(doc) }

        // Pages hosted only in the premium area are not served to visitors.
        if (images.isNotEmpty() && images.all { PREMIUM_PATH in it }) throw IOException(PREMIUM_ONLY)
        return images.mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    // Simply Gallery keeps the full, public page list as JSON next to the gallery.
    private fun galleryImages(doc: Document): List<String> = doc.select("script.sgb-data")
        .flatMap { it.data().parseAs<GalleryDto>().images }
        .map { it.url.toHttpUrl().toString() }
        .distinct()

    private fun contentImages(doc: Document): List<String> = doc
        .select("#tab-classic img, .wp-block-image img, .blocks-gallery-item img")
        .mapNotNull { img ->
            listOf("data-src", "data-lazy-src", "src")
                .map { img.absUrl(it) }
                .firstOrNull { "/wp-content/uploads/" in it }
        }
        .map { it.replace(SIZE_SUFFIX, "") }
        .distinct()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val categories = buildList {
            for (page in 1..CATEGORY_PAGES) {
                val url = "$baseUrl/wp-json/wp/v2/categories".toHttpUrl().newBuilder()
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("orderby", "count")
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("hide_empty", "true")
                    .addQueryParameter("_fields", "id,slug,name,count")
                    .build()
                val batch = client.get(url, Fetch.XHR).parseAs<List<TermDto>>()
                addAll(batch)
                if (batch.size < 100) break
            }
        }
        return FilterDataDto(categories).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val terms = data?.let { runCatching { it.parseAs<FilterDataDto>() }.getOrNull() }
        return FilterList(
            buildList {
                add(SortFilter())
                add(SectionFilter())
                if (terms != null) {
                    add(Filter.Separator())
                    add(Filter.Header("Tocca una volta per includere, due volte per escludere"))
                    add(
                        CategoryGroup(
                            terms.categories
                                .filter { it.count >= MIN_CATEGORY_COUNT && it.id !in CATEGORY_SKIP }
                                .filter { !BLOCKED_TEXT.containsMatchIn(it.slug) }
                                .sortedBy { it.plainName.lowercase(Locale.ITALIAN) }
                                .map { CategoryOption(it.plainName, it.id) },
                        ),
                    )
                }
                add(Filter.Separator())
                add(Filter.Header("Il sito non ha dati di popolarità: Popolari è in ordine casuale, nuovo a ogni apertura. «Consigliati» sono i titoli scelti dal sito. I contenuti con minori sono sempre esclusi."))
            },
        )
    }

    // The post text starts with the synopsis (on about half of the posts) and
    // goes on with page-builder shortcodes, base64 blocks and promotional
    // lines: keep what comes before the first promotional line.
    private fun synopsis(text: String): String? {
        val cleaned = text
            .replace(SPACES, " ")
            .replace(SHORTCODE, " ")
            .replace(LONG_TOKEN, " ")
        val end = PROMO_START.find(cleaned)?.range?.first ?: cleaned.length
        return cleaned.substring(0, end).replace(SPACES, " ").trim().takeIf { it.length >= MIN_DESCRIPTION }
    }

    private fun isAllowed(classes: Collection<String>, title: String): Boolean = classes.none { it in BLOCKED_CLASSES } && !BLOCKED_TEXT.containsMatchIn(title)

    private data class Query(
        val text: String = "",
        val sort: SortMode = SortMode.RANDOM,
        val ascending: Boolean = false,
        val section: Section = SECTIONS[0],
        val included: List<Int> = emptyList(),
        val excluded: List<Int> = emptyList(),
    )

    companion object {
        private const val PAGE_SIZE = 24
        private const val CATEGORY_PAGES = 6
        private const val MIN_CATEGORY_COUNT = 2
        private const val PREMIUM_PATH = "/area-premium/"

        private const val LIST_FIELDS = "id,slug,link,title,date_gmt,class_list,_links.wp:featuredmedia,_embedded"
        private const val DETAIL_FIELDS = "id,slug,link,title,date_gmt,class_list,content,_links.wp:featuredmedia,_links.wp:term,_embedded"
        private const val MIN_DESCRIPTION = 20

        private val SHORTCODE = Regex("""\[/?[a-z_]+[^\]]*]""")
        private val LONG_TOKEN = Regex("""\S{30,}""")
        private val SPACES = Regex("""[\s\u00A0]+""")
        private val PROMO_START = Regex(
            """hentai (ita )?premium|nella nostra nuova area premium|volume completo|capitoli (successivi|precedenti)|i fumetti e i videogiochi della no safe zone""",
            RegexOption.IGNORE_CASE,
        )

        private val SIZE_SUFFIX = Regex("""-\d+x\d+(?=\.(?:jpe?g|png|webp|avif)$)""")

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val BLOCKED_CLASSES: Set<String> =
            (BLOCKED_CATEGORIES.values + NON_READING_CATEGORIES.values).map { "category-$it" }.toSet()

        private val CATEGORY_SKIP: Set<Int> =
            SECTIONS.mapNotNull { it.id }.toSet() + BEST_CATEGORY_ID +
                BLOCKED_CATEGORIES.keys + NON_READING_CATEGORIES.keys

        private val HIDDEN_GENRES = setOf("best", "free", "mobile", "uncategorized")

        private const val PREMIUM_ONLY =
            "Le pagine di questo titolo sono solo nell'area premium del sito e non sono pubbliche."
    }
}
