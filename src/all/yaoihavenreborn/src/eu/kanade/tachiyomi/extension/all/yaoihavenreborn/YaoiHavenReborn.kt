package eu.kanade.tachiyomi.extension.all.yaoihavenreborn

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class YaoiHavenReborn : KeiSource() {

    override val supportsLatest = false

    override val supportsFilterFetching = true

    // The site lists its doujinshi by upload date only
    override suspend fun getPopularManga(page: Int) = getDoujinshi(listUrl(page).build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl(page).apply {
            if (query.isNotBlank()) addQueryParameter("name", query.trim())
            filters.filterIsInstance<UriPartFilter>().forEach { filter ->
                val (name, value) = filter.queryParameter
                if (value.isNotEmpty()) addQueryParameter(name, value)
            }
            val tags = filters.filterIsInstance<TagGroupFilter>().flatMap { it.state }
            tags.filter { it.isIncluded() }.ifEmpty { null }?.let { included -> addQueryParameter("tags", included.joinToString(",") { it.name }) }
            tags.filter { it.isExcluded() }.ifEmpty { null }?.let { excluded -> addQueryParameter("exclude", excluded.joinToString(",") { it.name }) }
        }.build()
        return getDoujinshi(url)
    }

    private fun listUrl(page: Int) = "$baseUrl/doujinshi/".toHttpUrl().newBuilder().apply {
        if (page > 1) addQueryParameter("page", page.toString())
    }

    // The list only exists as the data of the page's script, written by Django's escapejs
    private suspend fun getDoujinshi(url: HttpUrl): MangasPage {
        val script = client.get(url, Fetch.DOCUMENT).asJsoup().selectFirst("script:containsData(doujinshi: [)")!!.data()
        val mangas = ENTRY_REGEX.findAll(script.substringAfter("doujinshi: [").substringBefore("pages:")).map { entry ->
            val fields = FIELD_REGEX.findAll(entry.value).associate { it.groupValues[1] to it.groupValues[2].unescapeJs() }
            SManga.create().apply {
                this.url = "/doujinshi/${fields["slug"]!!}/"
                title = fields["name"]!!
                author = fields["artist"]
                thumbnail_url = fields["coverImage"]?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
                memo = buildJsonObject { put(IN_PROGRESS, "inProgress: true" in entry.value) }
            }
        }.toList()
        val page = PAGE_REGEX.find(script)!!.groupValues[1].toInt()
        val pages = PAGES_REGEX.find(script)!!.groupValues[1].toInt()
        return MangasPage(mangas, page < pages)
    }

    private fun String.unescapeJs() = replace(JS_ESCAPE_REGEX) { match ->
        val escaped = match.groupValues[1]
        if (escaped.length == 5) escaped.drop(1).toInt(16).toChar().toString() else escaped
    }

    // Details, chapter and pages are on the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val inProgress = manga.memo.getBooleanOrNull(IN_PROGRESS)
        val details = parseDetails(document, manga.url).apply {
            memo = manga.memo
            status = when (inProgress) {
                true -> SManga.ONGOING
                false -> SManga.COMPLETED
                null -> SManga.UNKNOWN
            }
            update_strategy = if (inProgress == false) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = parseDate(document.info("Updated:"))
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    private fun parseDetails(document: Document, path: String) = SManga.create().apply {
        url = path
        title = document.selectFirst("h1.text-h5")!!.text()
        author = document.info("Artist:")
        genre = document.select("v-chip[href*=tags=]").map { it.text() }.ifEmpty { null }?.joinToString()
        thumbnail_url = document.pageImages().firstOrNull()
        description = MarkdownDescription.Builder()
            .field("Language", document.info("Language:"))
            .field("Images", document.info("Image Count:"))
            .build()
    }

    private fun Document.info(label: String) = select("v-row").firstOrNull { it.selectFirst("v-col")?.text() == label }
        ?.select("v-col")?.getOrNull(1)?.text()?.ifEmpty { null }

    private fun Document.pageImages() = select("v-img[src^=/media/doujinshi/]").map { it.absUrl("src") }

    private fun parseDate(text: String?): Long {
        val (month, day, year) = DATE_REGEX.find(text ?: return 0L)?.destructured ?: return 0L
        return DATE_FORMAT.tryParseDate("${month.take(3)} $day $year", ZONE)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        // A list of the site, by artist, tags or search
        if (url.sitePath() == listOf("doujinshi")) {
            val listUrl = listUrl(page).apply {
                url.queryParameterNames.filter { it != "page" }.forEach { name ->
                    url.queryParameter(name)?.let { addQueryParameter(name, it) }
                }
            }.build()
            return getDoujinshi(listUrl)
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val path = url.sitePath() ?: return null
        if (path.size != 2 || path[0] != "doujinshi") return null
        return getMangaUpdate(SManga.create().apply { this.url = "/doujinshi/${path[1]}/" }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun HttpUrl.sitePath() = pathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .pageImages()
        .mapIndexed { i, image -> Page(i, imageUrl = image) }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/categories/", Fetch.XHR).parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<CategoriesDto>()?.results)

    companion object {
        private const val IN_PROGRESS = "inProgress"
        private val ENTRY_REGEX = Regex("""\{[^{}]*\}""")
        private val FIELD_REGEX = Regex("""(\w+): '((?:[^'\\]|\\.)*)'""")
        private val JS_ESCAPE_REGEX = Regex("""\\(u[0-9A-Fa-f]{4}|.)""")
        private val PAGE_REGEX = Regex("""\bpage: (\d+),""")
        private val PAGES_REGEX = Regex("""\bpages: (\d+),""")

        // Django's default format, "Nov. 5, 2026, 12:03 a.m.", "March 3, 2026, noon"
        private val DATE_REGEX = Regex("""^([A-Za-z]+)\.? (\d{1,2}), (\d{4})""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)
        private val ZONE = ZoneId.of("America/New_York")
    }
}
