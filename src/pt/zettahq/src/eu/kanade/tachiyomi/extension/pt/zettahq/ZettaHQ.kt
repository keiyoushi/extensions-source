package eu.kanade.tachiyomi.extension.pt.zettahq

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.Normalizer

@Source
abstract class ZettaHQ : KeiSource() {

    override val supportsLatest = false

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangasPageParse(client.get("$baseUrl/page/$page").asJsoup())

    private fun mangasPageParse(document: Document): MangasPage {
        val mangas = document.select("div.post-item article").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3 a")!!.let { anchor ->
            title = anchor.text()
            setUrlWithoutDomain(anchor.absUrl("href"))
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ==============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()

        var isCategoryEnable = false
        var isGenreEnable = false
        var isAuthorEnable = false

        filters
            .filterIsInstance<Sort>()
            .sortedByDescending { it.priority }
            .forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        val genresSelected = filter.state
                            .filter { it.state }
                            .joinToString("+") { it.id }
                            .takeIf(String::isNotEmpty) ?: return@forEach

                        if (isCategoryEnable) {
                            url.addQueryParameter("tag", genresSelected)
                            return@forEach
                        }

                        url.addPathSegment("tag")
                            .addPathSegment(genresSelected)

                        isGenreEnable = isGenreEnable.not()
                    }

                    is SelectFilter -> {
                        val selected = filter.selected()
                        if (selected.isBlank()) return@forEach

                        if (isCategoryEnable || isGenreEnable || isAuthorEnable) {
                            url.addQueryParameter(filter.query, selected)
                            return@forEach
                        }

                        url.addPathSegment(filter.query)
                            .addPathSegment(selected)

                        when {
                            filter.query.equals("autor", true) -> {
                                isAuthorEnable = isAuthorEnable.not()
                            }

                            filter.query.equals("category", true) -> {
                                isCategoryEnable = isCategoryEnable.not()
                            }

                            else -> {}
                        }
                    }

                    else -> {}
                }
            }

        url.addPathSegment("page")
            .addPathSegment(page.toString())
            .addQueryParameter("s", query)

        return mangasPageParse(client.get(url.build()).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val slug = url.pathSegments.last { it.isNotBlank() }
        return getMangaDetails("/$slug")
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga.url) else manga

        val chapterList = listOf(
            SChapter.create().apply {
                name = "Capítulo Único"
                url = manga.url
            },
        )

        return SMangaUpdate(updatedManga, chapterList)
    }

    private suspend fun getMangaDetails(path: String) = SManga.create().apply {
        val document = client.get(baseUrl + path).asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".content-container article img:first-child")?.absUrl("src")
        genre = document.select(".tags > a.tag").joinToString { it.text() }
        author = document.selectFirst("strong:contains(Autor) + a")?.text()
        status = SManga.COMPLETED
        setUrlWithoutDomain(document.location())
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select(".content-container article img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    // =============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/busca-avancada/").asJsoup()

        return FilterData(
            categories = parseOptions(document, "ofcategory"),
            authors = parseOptions(document, "ofautor"),
            characters = parseOptions(document, "ofpersonagem"),
            parodies = parseOptions(document, "ofparodia"),
            genres = parseGenres(document),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        return FilterList(
            SelectFilter(title = "Categorias", vals = filterData.categories.toTypedArray(), query = "category", priority = 3),
            Filter.Separator(),
            SelectFilter(title = "Personagens", vals = filterData.characters.toTypedArray(), query = "personagem"),
            Filter.Separator(),
            SelectFilter(title = "Autor", vals = filterData.authors.toTypedArray(), query = "autor", priority = 1),
            Filter.Separator(),
            SelectFilter(title = "Paródia", vals = filterData.parodies.toTypedArray(), query = "parodia"),
            Filter.Separator(),
            GenreList(title = "Gêneros", genres = filterData.genres, priority = 2),
        )
    }

    private fun parseGenres(document: Document): List<Genre> = document.select(".cat-item > label")
        .map { label ->
            Genre(
                name = label.text(),
                id = label.text().normalize(),
            )
        }

    private fun parseOptions(document: Document, attr: String): List<Pair<String, String>> {
        val options = mutableListOf("Todos" to "")

        options += document.select("select[name*=$attr] option").map { option ->
            option.text() to option.text().normalize()
        }

        return options
    }

    private fun String.normalize() = this
        .lowercase().trim()
        .replace(SPACE_REGEX, "-")
        .removeAccents()

    private fun String.removeAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace(ACCENT_REGEX, "")
    }

    companion object {
        val SPACE_REGEX = Regex("""\s+""")
        private val ACCENT_REGEX = Regex("""[\p{InCombiningDiacriticalMarks}]""")
    }
}
