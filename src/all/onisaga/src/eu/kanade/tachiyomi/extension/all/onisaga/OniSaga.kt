package eu.kanade.tachiyomi.extension.all.onisaga

import android.util.LruCache
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class OniSaga : KeiSource() {

    // The site's pages take up to 20 s to render and the "all" source loads every language at once; images are not limited
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds) {
        it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/api/media/")
    }

    private val siteLanguage get() = lang.takeIf { it != "all" }

    // The API pages with signed cursors: the one for each listing's next page
    private val cursors = LruCache<String, String>(100)

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, BrowseInput(PAGE_SIZE, languages = siteLanguage?.let(::listOf)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val yearFrom = filters.firstInstance<YearFromFilter>().year
        val yearTo = filters.firstInstance<YearToFilter>().year
        val input = BrowseInput(
            limit = PAGE_SIZE,
            q = query.trim().ifEmpty { null },
            genres = filters.firstInstance<GenreFilter>().terms,
            themes = filters.firstInstance<ThemeFilter>().terms,
            tags = filters.firstInstance<TagFilter>().terms,
            origin = filters.firstInstance<TypeFilter>().checked,
            form = filters.firstInstance<FormatFilter>().checked,
            status = filters.firstInstance<StatusFilter>().checked,
            demographic = filters.firstInstance<DemographicFilter>().checked,
            ratings = filters.firstInstance<RatingFilter>().checked,
            year = if (yearFrom != null || yearTo != null) YearRange(yearFrom, yearTo) else null,
            languages = siteLanguage?.let(::listOf),
            sort = filters.firstInstance<SortFilter>().value,
        )
        return browse(page, input)
    }

    private suspend fun browse(page: Int, input: BrowseInput): MangasPage {
        confirmAge()
        val listing = input.toJsonString()
        if (page > 1) input.cursor = cursors["$page$listing"]!!
        val result = client.post("$baseUrl/api/rpc/catalog/browse", Fetch.XHR, RpcBody(input).toJsonRequestBody())
            .parseAs<RpcBody<BrowseDto>>().json
        result.nextCursor?.let { cursors.put("${page + 1}$listing", it) }
        return MangasPage(result.items.map { it.toSManga() }, result.nextCursor != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val path = when (url.pathSegments[0]) {
            "title", "read" -> "/title/$slug"
            "manga" -> "/manga/$slug"
            else -> return null
        }
        val manga = SManga.create().apply { this.url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = titlePage(titleSlug(manga), siteLanguage)
        val gate = document.selectFirst("main[data-ui=content-gate]") ?: return SMangaUpdate(
            document.toSManga(),
            if (fetchChapters) document.chapterList() else chapters,
        )

        // The API gives such a title's chapters in one language only, oldest first
        val slug = document.location().toHttpUrl().pathSegments[1]
        val title = gatedView<SlugInput, GatedTitleDto>(gate, "account/titleView", SlugInput(slug))
        val units = if (siteLanguage == null || siteLanguage == title.language) title.units.asReversed() else emptyList()
        return SMangaUpdate(
            title.toSManga(slug),
            units.filter { it.releaseId != null }.map { unit ->
                SChapter.create().apply {
                    url = "/read/$slug/${unit.releaseId}"
                    setName(unit.label, unit.title)
                    scanlator = title.language.uppercase().takeIf { siteLanguage == null }
                }
            },
        )
    }

    private fun Document.toSManga() = SManga.create().apply {
        val series = selectFirst("script[type=\"application/ld+json\"]")!!.data().parseAs<SeriesDto>()
        url = "/title/${location().toHttpUrl().pathSegments[1]}"
        title = series.name
        thumbnail_url = selectFirst("section[aria-labelledby=work-title] img[srcset]")?.absUrl("src")?.let { fullSizeCover(it) }
        author = series.author.joinToString { it.name }.ifEmpty { null }
        artist = series.creator.joinToString { it.name }.ifEmpty { null }
        genre = (listOfNotNull(selectFirst("span[data-medium]:not([aria-hidden])")?.text()) + series.genre).joinToString().ifEmpty { null }
        status = when (selectFirst("dt:matchesOwn(^Status$) + dd")?.text()?.lowercase()) {
            "publishing" -> SManga.ONGOING
            "publication finished" -> SManga.COMPLETED
            "publication on hiatus" -> SManga.ON_HIATUS
            "publication cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .markdown(selectFirst("p#synopsis")?.wholeText())
            .altTitles(series.alternateName)
            .build()
    }

    private suspend fun Document.chapterList(): List<SChapter> {
        if (siteLanguage != null) return chapterPages(this, null)

        // The "all" source lists each language the title reads in, starting with the one this page shows
        val alternatives = extractNextJs<ReadingStartDto>()?.alternatives ?: return chapterPages(this, null)
        val current = alternatives.firstOrNull { it.current }?.let(::languageOf)
        val slug = location().toHttpUrl().pathSegments[1]
        val chapters = coroutineScope {
            alternatives.mapNotNull(::languageOf).distinct().map { language ->
                async {
                    val firstPage = if (language == current) this@chapterList else titlePage(slug, language)
                    chapterPages(firstPage, language)
                }
            }.awaitAll().flatten()
        }
        return chapters.sortedByDescending { it.chapter_number }
    }

    private fun languageOf(alternative: AlternativeDto) = baseUrl.toHttpUrl().resolve(alternative.href)?.queryParameter("lang")

    private suspend fun chapterPages(firstPage: Document, language: String?): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = firstPage
        while (true) {
            // Chapters not readable in this language stay in the list, without a link
            page.select("a[data-unit], details[data-unit]").flatMapTo(chapters) { it.toSChapters(language) }
            val next = page.selectFirst("nav[aria-label=\"Chapter pages\"] a[href*=\"cc=\"]") ?: break
            page = client.get(next.absUrl("href"), Fetch.DOCUMENT).asJsoup()
        }
        return chapters
    }

    // A chapter is a link, or a list of versions when several groups have it
    private fun Element.toSChapters(language: String?): List<SChapter> {
        val row = selectFirst("> summary") ?: this
        val label = row.selectFirst("> span:has(> [data-read-text])")!!.ownText()
        val title = row.selectFirst("> span[dir=auto]")?.text()
        val versions = if (tagName() == "details") select("li") else listOf(this)
        return versions.map { version ->
            SChapter.create().apply {
                val link = if (version.tagName() == "a") version else version.selectFirst("a[href^=/read/]")!!
                setUrlWithoutDomain(link.absUrl("href"))
                setName(label, title)
                date_upload = Instant.tryParse(version.selectFirst("time[datetime]")?.attr("datetime"))
                val group = version.select("a[href^=/group/]").joinToString { it.text() }.ifEmpty { null }
                scanlator = listOfNotNull(language?.uppercase(), group).joinToString(" - ").ifEmpty { null }
            }
        }
    }

    // Labels are a number or "Chapter N", and titles often only repeat them ("Vol 1 - Chap 1")
    private fun SChapter.setName(label: String, title: String?) {
        val number = CHAPTER_NUMBER_REGEX.matchEntire(label)?.groupValues?.get(1)
        val baseName = number?.let { "Chapter $it" } ?: label
        name = if (title == null || title == label || GENERIC_TITLE_REGEX.matches(title)) baseName else "$baseName: $title"
        number?.let { chapter_number = it.toFloat() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Chapters from before the site's move to /read/<title>/<release> are gone: a refresh lists the new ones
        check(chapter.url.count { it == '/' } == 3) { "Refresh the chapter list" }
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val gate = document.selectFirst("main[data-ui=content-gate]")
        val imageUrls = if (gate == null) {
            document.extractNextJs<ReaderDto>()!!.pages.map { it.url }
        } else {
            val (slug, releaseId) = document.location().toHttpUrl().pathSegments.let { it[1] to it[2] }
            // The image host refuses these pages without the age statement, whose cookie only goes to the main host
            gatedView<ReleaseInput, GatedReaderDto>(gate, "account/readerView", ReleaseInput(slug, releaseId)).bootstrap.pages
                .map { it.url.toHttpUrl().newBuilder().host(baseUrl.toHttpUrl().host).build().toString() }
        }
        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("title")
            .addPathSegment(titleSlug(manga))
            .addQueryParameter("tab", "related")
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select("section[aria-labelledby=rel-title] a[href^=/title/], [data-ui=work-card] a[href^=/title/]")
            .map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = link.selectFirst("strong, h3")!!.text()
                    thumbnail_url = link.selectFirst("img")?.absUrl("src")?.let { fullSizeCover(it) }
                }
            }
            .distinctBy { it.url }
    }

    private suspend fun titleSlug(manga: SManga): String {
        if (manga.url.startsWith("/title/")) return manga.url.substringAfter("/title/")
        // Entries saved from the previous site are under /manga/<old slug>, which redirects to the title's new slug
        val response = client.newBuilder().followRedirects(false).build()
            .head(getMangaUrl(manga), ensureSuccess = false)
        val location = response.use { it.header("Location") ?: throw HttpException(it.code) }
        return baseUrl.toHttpUrl().resolve(location)!!.pathSegments[1]
    }

    private suspend fun titlePage(slug: String, language: String?): Document {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("title")
            .addPathSegment(slug)
            .addQueryParameter("chapters", "all")
            .addQueryParameter("order", "desc")
            .apply { language?.let { addQueryParameter("lang", it) } }
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
    }

    // The pages of titles rated for adults are only a gate: the site loads them from its API for guests who stated
    // being 18 or older, and for accounts whose settings allow them
    private suspend inline fun <reified I, reified O> gatedView(gate: Element, method: String, input: I): O {
        if (gate.attr("data-gate") != "self-confirmation") {
            throw Exception("${gate.selectFirst("h1")!!.text()}: the site opens it only for an account allowed to see it")
        }
        confirmAge()
        return client.post("$baseUrl/api/rpc/$method", Fetch.XHR, RpcBody(input).toJsonRequestBody())
            .parseAs<RpcBody<O>>().json
    }

    // The statement the site asks guests for before showing titles rated for adults; it keeps it in a cookie for 180 days
    private suspend fun confirmAge() {
        if (client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any { it.name == AGE_COOKIE }) return
        val form = FormBody.Builder()
            .add("action", "confirm")
            .add("next", "/")
            .build()
        client.newBuilder().followRedirects(false).build()
            .post("$baseUrl/api/age-confirmation", Fetch.DOCUMENT, form, ensureSuccess = false)
            .close()
    }

    private fun fullSizeCover(url: String) = url.toHttpUrl().newBuilder().removeAllQueryParameters("w").build().toString()

    companion object {
        private const val PAGE_SIZE = 42
        private const val AGE_COOKIE = "__Host-onisaga.age_confirmed"
        private val CHAPTER_NUMBER_REGEX = Regex("""(?:ch(?:ap(?:ter)?)?\.?\s*)?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val GENERIC_TITLE_REGEX = Regex("""(?:vol(?:ume)?\.?\s*\d+(?:\.\d+)?\s*[-:,]?\s*)?ch(?:ap(?:ter)?)?\.?\s*\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)
    }
}
