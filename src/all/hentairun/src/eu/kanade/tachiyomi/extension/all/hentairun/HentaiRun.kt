package eu.kanade.tachiyomi.extension.all.hentairun

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

@Source
abstract class HentaiRun : KeiSource() {

    // The site's names for the languages its search filters on
    private val siteLanguage: String?
        get() = when (lang) {
            "en" -> "english"
            "ja" -> "japanese"
            "es" -> "spanish"
            "fr" -> "french"
            "ko" -> "korean"
            "de" -> "german"
            "ru" -> "russian"
            else -> null
        }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(POPULAR_SORT)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("search/")
            .apply {
                query.trim().lowercase().takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
                addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.value ?: SORTS.first().second)
                filters.firstInstanceOrNull<CategoryFilter>()?.checked?.takeIf { it.isNotEmpty() }
                    ?.let { addQueryParameter("categories", it.joinToString(",")) }
                siteLanguage?.let { addQueryParameter("langs", it) }
                if (page > 1) addQueryParameter("page", page.toString())
            }
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.encodedPathSegments.filter(String::isNotEmpty)
        val listUrl = when (segments.firstOrNull()) {
            "gallery", "view" -> return super.getMangasByUrl(url, page)
            "search" -> baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("search/")
                .encodedQuery(url.encodedQuery)
                .removeAllQueryParameters("page")
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
            // Pages of the other lists are /page/<n>/ after their path, such as /tag/<name>/popular/page/2/
            in LIST_PATHS -> baseUrl.toHttpUrl().newBuilder()
                .addEncodedPathSegments(segments.takeWhile { it != "page" }.joinToString("/") + "/")
                .apply { if (page > 1) addPathSegments("page/$page/") }
                .build()
            else -> return MangasPage(emptyList(), false)
        }
        return client.get(listUrl, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.firstOrNull() !in setOf("gallery", "view")) return null
        val id = segments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = "/gallery/$id/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val id = manga.url.substringAfter("/gallery/").substringBefore('/')
        val chapter = SChapter.create().apply {
            url = "/view/$id/1/"
            name = "Chapter"
            scanlator = document.info("Groups").joinToString().ifEmpty { null }
        }
        return SMangaUpdate(document.toSManga(manga.url), listOf(chapter))
    }

    private fun Document.toSManga(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("h1")!!.text()
        thumbnail_url = selectFirst("a[href^=/view/] img")?.absUrl("src")
        val artists = info("Artists")
        author = artists.ifEmpty { info("Groups") }.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }
        genre = info("Tags").joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Parodies", info("Parodies"))
            .field("Characters", info("Characters"))
            .field("Groups", info("Groups"))
            .field("Languages", info("Languages"))
            .field("Category", info("Category"))
            .field("Pages", select("span:containsOwn(Pages:) + span").text())
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    // The names under a heading of the gallery's details, such as "Tags" or "Artists"
    private fun Document.info(heading: String): List<String> = select("span")
        .filter { it.text() == heading }
        .flatMap { it.parent()!!.select("> div > a > span:first-child") }
        .map { it.text() }
        .distinct()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .extractNextJs<Reader>()!!
        .toPages(baseUrl.toHttpUrl().host)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        CategoryFilter(),
    )

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("a[aria-label][href^=/gallery/]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                // Titles in lists are escaped twice
                title = Parser.unescapeEntities(link.attr("aria-label"), false)
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        // The last page of a list has no "Next" link (a button in search results)
        return MangasPage(mangas, select("a, button").any { it.text() == "Next ›" })
    }

    companion object {
        private val LIST_PATHS = setOf("tag", "parody", "character", "artist", "group", "category", "language")
    }
}
