package eu.kanade.tachiyomi.extension.en.coffeemangaunoriginal

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

// The site was rebuilt on a custom child theme ("mk3"/Utoon skin). The
// madara_load_more AJAX endpoint answers "forbidden", so browsing uses plain
// archive pages. Details use custom hero markup, chapters ship as embedded
// JSON, and only the reader keeps standard Madara markup.
@Source
abstract class CoffeeManga : Madara() {

    override val supportsRelatedMangas = false
    override val supportsFilterFetching = false

    // Browse

    private fun archiveUrl(page: Int, orderby: String): HttpUrl = "$baseUrl/$mangaSubString/".toHttpUrl().newBuilder().apply {
        if (page > 1) addPathSegments("page/$page/")
        addQueryParameter("m_orderby", orderby)
    }.build()

    private suspend fun archivePage(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("a.nextpostslink, a.next.page-numbers") != null)
    }

    // Archive/search cards are bare <a class="acard"> without a Madara post ID,
    // so entries are keyed by their path instead.
    override fun parseArchive(document: Document): List<SManga> = document.select("a.acard").mapNotNull { element ->
        val path = element.attr("abs:href").takeIf(String::isNotBlank)
            ?.toHttpUrl()?.encodedPath
            ?: return@mapNotNull null
        SManga.create().apply {
            url = path
            title = element.attr("title").ifBlank { element.selectFirst(".ac-t")!!.text() }
            thumbnail_url = element.selectFirst("img")?.let { imageFromElement(it) }?.let { processThumbnail(it, true) }
            memo = mangaMemo(path, emptyList())
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = archivePage(archiveUrl(page, "views"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = archivePage(archiveUrl(page, "latest"))

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = if (page == 1) baseUrl.toHttpUrl().newBuilder() else "$baseUrl/page/$page/".toHttpUrl().newBuilder()
        builder.addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
        filters.firstInstanceOrNull<SortFilter>()?.takeIf { it.state != 0 }?.let {
            builder.addQueryParameter("m_orderby", it.toUriPart())
        }
        return archivePage(builder.build())
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("The site only supports text search and sorting"),
        Filter.Separator(),
        SortFilter(intl["order_by_filter_title"], orderByFilterOptions),
    )

    // Details

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = SManga.create().apply {
        url = preserveUrl?.takeIf { !it.all(Char::isDigit) } ?: id
        title = document.selectFirst("h1.htitle")!!.text()
        thumbnail_url = document.selectFirst(".hposter__card img")?.let { imageFromElement(it) }
        description = buildString {
            document.selectFirst("#syn")?.text()?.takeIf(String::isNotEmpty)?.let(::append)
            document.selectFirst(".sir--wide .v")?.ownText()?.takeIf(String::isNotEmpty)?.let { alt ->
                if (isNotEmpty()) append("\n\n")
                append("${intl["alt_names_heading"]} ").append(alt)
            }
        }.ifBlank { null }
        status = document.selectFirst(".htag--status")?.text()?.toStatus() ?: SManga.UNKNOWN
        val genres = document.select(".hchips--genres a.chip").mapNotNull { chip ->
            val href = chip.attr("abs:href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath
            GenreRoute(chip.text(), path.trimEnd('/').substringAfterLast('/'), path)
        }
        genre = (
            genres.map(GenreRoute::name) +
                document.select(".hchips--tags a.chip").eachText()
            )
            .distinctBy(String::lowercase)
            .joinToString()
            .ifBlank { null }
        memo = mangaMemo(
            path = document.location().toHttpUrl().encodedPath,
            genres = genres,
            legacyId = id.takeIf { preserveUrl?.all(Char::isDigit) == false },
        )
    }

    // Chapters (embedded JSON rendered client-side by mk-chapters.js)

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val document = mangaPage ?: client.get("$baseUrl$mangaPath").asJsoup()
        val data = document.selectFirst("script#mk-chapters-data")?.data() ?: return emptyList()
        return data.parseAs<ChaptersData>().items.map { item ->
            SChapter.create().apply {
                url = item.url.toHttpUrl().encodedPath.trimEnd('/').substringAfterLast('/')
                name = item.name
                date_upload = parseChapterDate(item.ago)
                memo = buildJsonObject { put("mangaPath", mangaPath) }
            }
        }
    }
}

@Serializable
private class ChaptersData(val items: List<ChapterItem> = emptyList())

@Serializable
private class ChapterItem(
    val name: String,
    val url: String,
    val ago: String? = null,
)

private class SortFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun toUriPart() = options[state].second
}
