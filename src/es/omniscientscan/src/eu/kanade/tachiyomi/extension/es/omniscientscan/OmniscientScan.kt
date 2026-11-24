package eu.kanade.tachiyomi.extension.es.omniscientscan

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
import keiyoushi.utils.getBooleanOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.Normalizer
import java.time.format.DateTimeFormatter

@Source
abstract class OmniscientScan : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = getLibrary("", FilterList(SortFilter(SORT_VISITS)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getLibrary("", FilterList(SortFilter(SORT_RECENT)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getLibrary(query, filters)

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSiteUrl() || url.pathSegments != listOf("biblioteca")) return super.getMangasByUrl(url, page)
        val filters = AttributeFilter.entries.mapNotNull { attribute ->
            url.queryParameter(attribute.param)?.let { CardFilter(attribute, listOf(it to it), state = 1) }
        }
        return getLibrary(url.queryParameter("q").orEmpty(), FilterList(filters))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (!url.isSiteUrl() || segments.size < 2 || segments[0] != "obra" || segments[1].isEmpty()) return null
        val manga = SManga.create().apply { this.url = "/obra/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo.getBooleanOrNull("locked") == true) return emptyList()
        return client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .select("#readerImages img[data-file]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/biblioteca", Fetch.DOCUMENT).asJsoup()
        return AttributeFilter.entries.associate { attribute ->
            attribute.name to document.select("[data-set=${attribute.param}][data-v]")
                .filterNot { it.attr("data-v") in IGNORED_VALUES }
                .map { it.text() to it.attr("data-v") }
                .distinctBy { it.second }
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<Map<String, List<Pair<String, String>>>>())

    private suspend fun getLibrary(query: String, filters: FilterList): MangasPage {
        val search = query.normalized()
        val cardFilters = filters.filterIsInstance<CardFilter>()
        val comparator = (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).comparator
        val mangas = client.get("$baseUrl/biblioteca", Fetch.DOCUMENT).asJsoup()
            .select("article.lbx-card[data-kind=serie]")
            .filter { card -> card.attr("data-title").normalized().contains(search) && cardFilters.all { it.matches(card) } }
            .sortedWith(comparator)
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".lbx-info h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst(".lbx-cover img")?.absUrl("src")
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst("h1.fx-title")!!.text()
        thumbnail_url = selectFirst(".fx-cover img.cover")?.absUrl("src")
        genre = detail("Género")?.split(",")?.mapNotNull { it.trim().ifEmpty { null } }?.joinToString()?.ifEmpty { null }
        status = selectFirst(".fx-dl div:has(dt:containsOwn(Estado)) .ok")?.let {
            when {
                it.hasClass("end") -> SManga.COMPLETED
                it.hasClass("pause") -> SManga.ON_HIATUS
                else -> SManga.ONGOING
            }
        } ?: SManga.UNKNOWN
        description = MarkdownDescription.Builder()
            .rating(selectFirst(".fx-stats [data-rt-score]")?.text()?.toDoubleOrNull(), max = 5, votes = selectFirst(".fx-stats [data-rt-votes]")?.text()?.toIntOrNull())
            .info(detail("Tipo"), detail("Publicado"))
            .description(selectFirst("#fxSynText")?.text())
            .field("Scan", select(".scan-team-body strong").map { it.text() })
            .altTitles(listOfNotNull(selectFirst("p.fx-alt")?.textOrNull()))
            .build()
    }

    private fun Document.detail(label: String) = selectFirst(".fx-dl div:has(dt:containsOwn($label)) dd")?.textOrNull()

    private fun Document.toSChapters(): List<SChapter> = select("#chapterList a.chapter").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            val locked = element.hasClass("is-locked")
            val chapterName = element.selectFirst(".chapter-copy strong")!!.text()
            name = if (locked) "🔒 $chapterName" else chapterName
            chapter_number = element.attr("data-number").toFloatOrNull() ?: -1f
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".chapter-date")?.attr("title"))
            scanlator = element.selectFirst(".chapter-by")?.text()?.removePrefix("◈")?.trim()?.ifEmpty { null }
            if (locked) memo = buildJsonObject { put("locked", true) }
        }
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    private fun String.normalized() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "").lowercase().trim()

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
        private val IGNORED_VALUES = setOf("all", "novela-ligera")
    }
}
