package eu.kanade.tachiyomi.extension.it.hentaizone

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

/**
 * Hentai Zone is a WordPress site. Lists come from the WordPress REST API, which
 * supports AND/exclusion on categories and tags; "Popolari" comes from the HTML
 * archive ordered by comment count, the only popularity signal the site exposes.
 *
 * Premium posts are served by the site only to a logged-in Patreon subscriber.
 * The app shares the WebView cookies with this source, so logging in through
 * the WebView is enough; nothing here bypasses the site's own access check.
 */
@Source
abstract class HentaiZone : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)

    override fun getHomeUrl(): String = "$baseUrl/hentai-manga/"

    override suspend fun getPopularManga(page: Int): MangasPage = popularListing(page, Query())

    override suspend fun getLatestUpdates(page: Int): MangasPage = apiListing(page, Query(sort = SortMode.DATE))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val categories = filters.firstInstanceOrNull<CategoryGroup>()?.state.orEmpty()
        val tags = filters.firstInstanceOrNull<TagGroup>()?.state.orEmpty()
        val search = Query(
            text = query.trim(),
            sort = sort?.mode ?: SortMode.POPULAR,
            ascending = sort?.ascending ?: false,
            section = filters.firstInstanceOrNull<SectionFilter>()?.section ?: SECTIONS[0],
            access = filters.firstInstanceOrNull<AccessFilter>()?.access ?: Access.ALL,
            includedCategories = categories.filter { it.state == Filter.TriState.STATE_INCLUDE },
            excludedCategories = categories.filter { it.state == Filter.TriState.STATE_EXCLUDE },
            includedTags = tags.filter { it.state == Filter.TriState.STATE_INCLUDE },
            excludedTags = tags.filter { it.state == Filter.TriState.STATE_EXCLUDE },
        )
        // A tapped genre arrives as a plain query with no filters set: browse that term.
        val noTermFilters = categories.all { it.state == Filter.TriState.STATE_IGNORE } &&
            tags.all { it.state == Filter.TriState.STATE_IGNORE }
        if (search.text.isNotEmpty() && noTermFilters) {
            resolveTerm(search.text)?.let { (isTag, id) ->
                val option = TermOption(search.text, id, "")
                val genre = Query(
                    sort = SortMode.DATE,
                    section = SECTIONS.last(),
                    includedCategories = if (isTag) emptyList() else listOf(option),
                    includedTags = if (isTag) listOf(option) else emptyList(),
                )
                return apiListing(page, genre)
            }
        }
        return if (search.sort == SortMode.POPULAR) popularListing(page, search) else apiListing(page, search)
    }

    // Resolves a genre name (as shown on a chip) to its category or tag id.
    private suspend fun resolveTerm(name: String): Pair<Boolean, Int>? {
        for ((taxonomy, isTag) in listOf("categories" to false, "tags" to true)) {
            val url = "$baseUrl/wp-json/wp/v2/$taxonomy".toHttpUrl().newBuilder()
                .addQueryParameter("search", name)
                .addQueryParameter("per_page", "20")
                .addQueryParameter("_fields", "id,slug,name")
                .build()
            client.get(url, Fetch.XHR).parseAs<List<TermDto>>()
                .firstOrNull { it.plainName.equals(name, ignoreCase = true) }
                ?.let { return isTag to it.id }
        }
        return null
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.singleOrNull { it.isNotEmpty() } ?: return null
        val post = fetchPost(slug) ?: return null
        return post.takeIf { isAllowed(it.classList, it.plainTitle) }?.toSManga()
    }

    private suspend fun apiListing(page: Int, query: Query): MangasPage {
        val includedCategories = buildList {
            query.section.id?.let { add(it) }
            addAll(query.includedCategories.map { it.id })
            if (query.access == Access.PREMIUM) add(PREMIUM_CATEGORY_ID)
        }
        val excludedCategories = buildList {
            addAll(BLOCKED_CATEGORIES.keys)
            addAll(NON_READING_CATEGORIES.keys)
            addAll(query.excludedCategories.map { it.id })
            if (query.access == Access.FREE) add(PREMIUM_CATEGORY_ID)
        }
        val excludedTags = BLOCKED_TAGS.keys + query.excludedTags.map { it.id }
        val orderBy = when {
            query.sort == SortMode.RELEVANCE && query.text.isEmpty() -> "date"
            else -> query.sort.apiOrderBy ?: "date"
        }

        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", API_PAGE_SIZE.toString())
            if (includedCategories.isNotEmpty()) {
                addQueryParameter("categories[terms]", includedCategories.joinToString(","))
                addQueryParameter("categories[operator]", "AND")
            }
            addQueryParameter("categories_exclude", excludedCategories.joinToString(","))
            if (query.includedTags.isNotEmpty()) {
                addQueryParameter("tags[terms]", query.includedTags.joinToString(",") { it.id.toString() })
                addQueryParameter("tags[operator]", "AND")
            }
            addQueryParameter("tags_exclude", excludedTags.joinToString(","))
            if (query.text.isNotEmpty()) addQueryParameter("search", query.text)
            addQueryParameter("orderby", orderBy)
            addQueryParameter("order", if (query.ascending) "asc" else "desc")
            addQueryParameter("_embed", "wp:featuredmedia")
            addQueryParameter("_fields", LIST_FIELDS)
        }.build()

        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val mangas = response.parseAs<List<PostDto>>()
            .filter { isAllowed(it.classList, it.plainTitle) }
            .map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    // Two archive pages per app page: the cards dropped by the exclusions would
    // otherwise leave short or empty pages, and an empty page stops paging.
    private suspend fun popularListing(page: Int, query: Query): MangasPage = coroutineScope {
        val first = async { client.get(popularUrl(page * 2 - 1, query), Fetch.DOCUMENT).asJsoup() }
        val second = async { client.get(popularUrl(page * 2, query), Fetch.DOCUMENT).asJsoup() }
        val firstDoc = first.await()
        val secondDoc = second.await()

        val cards = (parseCards(firstDoc) + parseCards(secondDoc))
            .filter { card -> card.isAllowedFor(query) }
            .distinctBy { it.slug }
        val covers = fetchMissingCovers(cards)
        val mangas = cards.map { it.toSManga(it.thumbnail ?: covers[it.slug]) }
        MangasPage(mangas, hasNextArchivePage(secondDoc, page * 2))
    }

    // The theme's own archive template (used without a category) has no images.
    private suspend fun fetchMissingCovers(cards: List<Card>): Map<String, String?> {
        val ids = cards.filter { it.thumbnail == null }.mapNotNull { it.postId }
        if (ids.isEmpty()) return emptyMap()
        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("include", ids.joinToString(","))
            .addQueryParameter("per_page", ids.size.toString())
            .addQueryParameter("_embed", "wp:featuredmedia")
            .addQueryParameter("_fields", LIST_FIELDS)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<PostDto>>().associate { it.slug to it.thumbnail }
    }

    private fun popularUrl(archivePage: Int, query: Query): HttpUrl {
        val categories = buildList {
            query.section.slug?.let { add(it) }
            addAll(query.includedCategories.map { it.slug })
            if (query.access == Access.PREMIUM) add(PREMIUM_CATEGORY_SLUG)
        }
        return baseUrl.toHttpUrl().newBuilder().apply {
            if (categories.isEmpty()) {
                addQueryParameter("post_type", "post")
            } else {
                addQueryParameter("category_name", categories.joinToString("+"))
            }
            if (query.includedTags.isNotEmpty()) {
                addQueryParameter("tag", query.includedTags.joinToString("+") { it.slug })
            }
            if (query.text.isNotEmpty()) addQueryParameter("s", query.text)
            addQueryParameter("orderby", "comment_count")
            addQueryParameter("order", if (query.ascending) "asc" else "desc")
            if (archivePage > 1) addQueryParameter("paged", archivePage.toString())
        }.build()
    }

    // Category archives use Elementor cards, the plain archive uses the theme's entries.
    private fun parseCards(doc: Document): List<Card> = doc.select(CARD_SELECTOR).mapNotNull { article ->
        val link = article.selectFirst(".elementor-post__title a[href], .entry-title a[href]")
            ?: return@mapNotNull null
        val slug = link.absUrl("href").toHttpUrl().pathSegments.singleOrNull { it.isNotEmpty() }
            ?: return@mapNotNull null
        val image = article.selectFirst("img")
        val classes = article.classNames()
        Card(
            slug = slug,
            title = link.text().ifEmpty { return@mapNotNull null },
            thumbnail = (image?.absUrl("data-src")?.ifEmpty { null } ?: image?.absUrl("src"))?.ifEmpty { null },
            classes = classes,
            postId = classes.firstNotNullOfOrNull { POST_ID_CLASS.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() },
        )
    }

    private fun hasNextArchivePage(doc: Document, archivePage: Int): Boolean {
        val lastPage = doc.select("a.page-numbers[href]").maxOfOrNull { link ->
            val href = link.attr("href")
            (ARCHIVE_PAGE.find(href) ?: ARCHIVE_PAGED.find(href))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        return if (lastPage == null) {
            doc.select(CARD_SELECTOR).size >= ARCHIVE_PAGE_SIZE
        } else {
            lastPage > archivePage
        }
    }

    private fun Card.isAllowedFor(query: Query): Boolean {
        if (!isAllowed(classes, title)) return false
        val premium = "category-$PREMIUM_CATEGORY_SLUG" in classes
        if (query.access == Access.FREE && premium) return false
        if (query.excludedCategories.any { "category-${it.slug}" in classes }) return false
        return query.excludedTags.none { "tag-${it.slug}" in classes }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trim('/')
        val (found, page) = coroutineScope {
            val post = async { fetchPost(slug, details = true) }
            val page = async { client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup() }
            post.await() to page.await()
        }
        val post = found ?: throw IOException("Titolo non trovato su Hentai Zone.")
        if (!isAllowed(post.classList, post.plainTitle)) {
            throw IOException("Questo titolo è escluso dall'estensione.")
        }
        val premium = "category-$PREMIUM_CATEGORY_SLUG" in post.classList
        val series = seriesChapters(page, post)
        val pageCount = galleryImages(page).size

        manga.apply {
            title = post.plainTitle
            post.thumbnail?.let { thumbnail_url = it }
            genre = post.terms
                .filter { it.slug !in HIDDEN_GENRES }
                .joinToString { it.plainName }
            description = listOfNotNull(
                pageCount.takeIf { it > 0 }?.let { "Pagine: $it" },
                post.contentText.takeIf { it.length >= MIN_DESCRIPTION },
                PREMIUM_DESCRIPTION.takeIf { premium },
            ).joinToString("\n\n").ifEmpty { null }
            status = SManga.COMPLETED
            // A series gets new chapters; a one-shot never changes.
            update_strategy = if (series.isEmpty()) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        }

        val chapters = series.ifEmpty {
            listOf(
                SChapter.create().apply {
                    url = "/${post.slug}/"
                    name = if (premium) "Capitolo unico (Premium)" else "Capitolo unico"
                    chapter_number = 1f
                    date_upload = DATE_FORMAT.tryParse(post.dateGmt)
                },
            )
        }
        return SMangaUpdate(manga, chapters)
    }

    private fun galleryImages(doc: Document): List<String> = doc.select("div.gff-gallery[data-images]")
        .flatMap { it.attr("data-images").parseAs<List<GalleryImageDto>>() }
        .map { it.src.toHttpUrl().toString() }
        .distinct()

    // Each chapter of a series is its own post; the post page lists them all.
    private fun seriesChapters(page: Document, post: PostDto): List<SChapter> {
        val links = page.select("ul.hzc-list a[href]")
        if (links.size < 2) return emptyList()
        return links.mapNotNull { link ->
            val chapterSlug = link.absUrl("href").toHttpUrlOrNull()?.pathSegments?.singleOrNull { it.isNotEmpty() }
                ?: return@mapNotNull null
            SChapter.create().apply {
                url = "/$chapterSlug/"
                name = link.text()
                chapter_number = CHAPTER_NUMBER.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
                if (chapterSlug == post.slug) date_upload = DATE_FORMAT.tryParse(post.dateGmt)
            }
        }.distinctBy { it.url }.asReversed()
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
        // Never reuse a cached copy: after logging in, the same URL must be read again.
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT, CacheControl.FORCE_NETWORK).asJsoup()

        val images = galleryImages(doc)
        if (images.isNotEmpty()) {
            return images.mapIndexed { index, image -> Page(index, imageUrl = image) }
        }

        val premium = doc.selectFirst("a[href*=patreon.com/checkout]") != null
        val loggedIn = doc.body().hasClass("logged-in") || doc.selectFirst("a[href*=ps_oauth_start]") == null
        when {
            premium && !loggedIn -> throw IOException(LOGIN_REQUIRED)
            premium -> throw IOException(SUBSCRIPTION_REQUIRED)
            else -> return emptyList()
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val categories = client.get(termsUrl("categories", 1), Fetch.XHR).parseAs<List<TermDto>>()
        val tags = buildList {
            for (page in 1..TAG_PAGES) {
                val batch = client.get(termsUrl("tags", page), Fetch.XHR).parseAs<List<TermDto>>()
                addAll(batch)
                if (batch.size < TERMS_PAGE_SIZE) break
            }
        }
        return FilterDataDto(categories, tags).toJsonElement()
    }

    private fun termsUrl(taxonomy: String, page: Int): HttpUrl = "$baseUrl/wp-json/wp/v2/$taxonomy".toHttpUrl().newBuilder()
        .addQueryParameter("per_page", TERMS_PAGE_SIZE.toString())
        .addQueryParameter("page", page.toString())
        .addQueryParameter("orderby", "count")
        .addQueryParameter("order", "desc")
        .addQueryParameter("hide_empty", "true")
        .addQueryParameter("_fields", "id,slug,name,count")
        .build()

    override fun getFilterList(data: JsonElement?): FilterList {
        val terms = data?.let { runCatching { it.parseAs<FilterDataDto>() }.getOrNull() }
        return FilterList(
            buildList {
                add(SortFilter())
                add(SectionFilter())
                add(AccessFilter())
                if (terms != null) {
                    add(Filter.Separator())
                    add(Filter.Header("Tocca una volta per includere, due volte per escludere"))
                    add(CategoryGroup(terms.categories.toOptions(CATEGORY_SKIP)))
                    add(TagGroup(terms.tags.filter { it.count >= MIN_TAG_COUNT }.toOptions(BLOCKED_TAGS.values.toSet())))
                }
                add(Filter.Separator())
                add(Filter.Header("Popolari = più commentati. I contenuti con minori sono sempre esclusi."))
            },
        )
    }

    private fun List<TermDto>.toOptions(skip: Set<String>): List<TermOption> = this
        .filter { it.slug !in skip && !BLOCKED_TEXT.containsMatchIn(it.slug) }
        .sortedBy { it.plainName.lowercase(Locale.ITALIAN) }
        .map { TermOption(it.plainName, it.id, it.slug) }

    private fun isAllowed(classes: Collection<String>, title: String): Boolean = classes.none { it in BLOCKED_CLASSES } && !BLOCKED_TEXT.containsMatchIn(title)

    private class Query(
        val text: String = "",
        val sort: SortMode = SortMode.POPULAR,
        val ascending: Boolean = false,
        val section: Section = SECTIONS[0],
        val access: Access = Access.ALL,
        val includedCategories: List<TermOption> = emptyList(),
        val excludedCategories: List<TermOption> = emptyList(),
        val includedTags: List<TermOption> = emptyList(),
        val excludedTags: List<TermOption> = emptyList(),
    )

    private class Card(
        val slug: String,
        val title: String,
        val thumbnail: String?,
        val classes: Set<String>,
        val postId: Int?,
    ) {
        fun toSManga(cover: String?) = SManga.create().apply {
            url = "/$slug/"
            title = this@Card.title
            thumbnail_url = cover
        }
    }

    companion object {
        private const val API_PAGE_SIZE = 24
        private const val ARCHIVE_PAGE_SIZE = 12
        private const val TERMS_PAGE_SIZE = 100
        private const val TAG_PAGES = 3
        private const val MIN_TAG_COUNT = 5

        private const val LIST_FIELDS = "id,slug,title,date_gmt,class_list,_links.wp:featuredmedia,_embedded"
        private const val DETAIL_FIELDS = "id,slug,title,date_gmt,class_list,content,_links.wp:featuredmedia,_links.wp:term,_embedded"
        private const val MIN_DESCRIPTION = 20

        private val CHAPTER_NUMBER = Regex("""(\d+(?:[.,]\d+)?)""")

        private const val CARD_SELECTOR = "article.elementor-post, article.type-post"
        private val POST_ID_CLASS = Regex("""post-(\d+)""")
        private val ARCHIVE_PAGE = Regex("""/page/(\d+)""")
        private val ARCHIVE_PAGED = Regex("""[?&]paged=(\d+)""")

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val BLOCKED_CLASSES: Set<String> =
            BLOCKED_CATEGORIES.values.map { "category-$it" }.toSet() +
                NON_READING_CATEGORIES.values.map { "category-$it" } +
                BLOCKED_TAGS.values.map { "tag-$it" }

        private val CATEGORY_SKIP: Set<String> =
            SECTIONS.mapNotNull { it.slug }.toSet() +
                PREMIUM_CATEGORY_SLUG +
                BLOCKED_CATEGORIES.values +
                NON_READING_CATEGORIES.values

        private val HIDDEN_GENRES = setOf("italiano", PREMIUM_CATEGORY_SLUG)

        private const val PREMIUM_DESCRIPTION =
            "Titolo Premium: il sito mostra le pagine solo agli abbonati Patreon che hanno effettuato l'accesso. " +
                "Per accedere apri la WebView (icona del mappamondo), tocca «Entra con Patreon» e completa il login."

        private const val LOGIN_REQUIRED =
            "Titolo Premium: serve l'accesso. Apri la WebView, tocca «Entra con Patreon», " +
                "completa il login, torna indietro e riprova."

        private const val SUBSCRIPTION_REQUIRED =
            "Hai effettuato l'accesso, ma il sito non mostra le pagine di questo titolo Premium. " +
                "Controlla che l'abbonamento Patreon sia attivo e collegato a Hentai Zone."
    }
}
