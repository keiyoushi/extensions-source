package eu.kanade.tachiyomi.extension.fr.bdplus

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.Normalizer

@Source
abstract class BDplus : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val config = client.get(baseUrl).asJsoup()
            .selectFirst("script#homeCfg")!!
            .data()
            .parseAs<HomeConfigDto>()
        val updates = client.get(baseUrl + config.upd).parseAs<UpdatesDto>().g

        return updates.toMangasPage(page) { it.toSManga(baseUrl) }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchTerm = query.trim().normalize()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val comparator = (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).comparator()

        val entries = client.get("$baseUrl/data/search_corpus.json").parseAs<CorpusDto>().docs
            .filter { genre == null || genre in it.genres }
            .filter { entry -> searchTerm.isEmpty() || entry.searchTerms().any { searchTerm in it.normalize() } }
            .sortedWith(comparator)

        return entries.toMangasPage(page) { it.toSManga(baseUrl) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "serie_static") return null
        val slug = url.pathSegments.getOrNull(1)?.removeSuffix(".html") ?: return null

        return client.get(seriesUrl(slug)).asJsoup().toSManga(slug)
    }

    override fun getMangaUrl(manga: SManga) = seriesUrl(manga.url)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val chapterList = document.select("#tl a.tr").map { element ->
            SChapter.create().apply {
                url = element.attr("data-k")
                name = listOfNotNull(
                    element.selectFirst(".tn")?.textOrNull(),
                    element.selectFirst(".tt")?.textOrNull(),
                ).joinToString(" : ")
            }
        }

        return SMangaUpdate(
            manga = document.toSManga(manga.url),
            chapters = chapterList.reversed(),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/lecture.html?path=${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val tomeUrl = "$baseUrl/_converted/${chapter.url}"

        return client.get("$tomeUrl/pages.json").parseAs<PagesDto>().pages.mapIndexed { index, page ->
            val imageUrl = if (page.url.startsWith("/")) baseUrl + page.url else "$tomeUrl/${page.url}"
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    private fun seriesUrl(slug: String) = "$baseUrl/serie_static/$slug.html"

    private fun Document.toSManga(slug: String) = SManga.create().apply {
        url = slug
        title = selectFirst("h1#fhT")!!.text()
        author = select(".fh-au b").joinToString { it.text() }.ifEmpty { null }
        genre = select(".fh-meta a.pill.pg").joinToString { it.ownText() }.ifEmpty { null }
        description = selectFirst("p.fh-lead")?.text()
        thumbnail_url = selectFirst(".fh-cov img")?.absUrl("src")
    }

    private fun <T> List<T>.toMangasPage(page: Int, transform: (T) -> SManga) = MangasPage(
        mangas = drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).map(transform),
        hasNextPage = size > page * PAGE_SIZE,
    )

    private fun String.normalize() = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
        .lowercase()

    companion object {
        private const val PAGE_SIZE = 24
        private val DIACRITICS_REGEX = Regex("""\p{Mn}+""")
    }
}
