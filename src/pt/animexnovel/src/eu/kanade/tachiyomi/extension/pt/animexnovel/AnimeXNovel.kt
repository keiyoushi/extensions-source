package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class AnimeXNovel : KeiSource() {

    override val supportsFilterFetching: Boolean = true

    override fun OkHttpClient.Builder.configureClient() = readTimeout(1.minutes)
        .callTimeout(1.minutes)
        .rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            listOf(
                BoxList("", supportsTypeSource.map { BoxValue("", it) }).apply {
                    state.forEach { it.state = true }
                },
            ),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup()
            .select("div:contains(Últimos Mangás) + .axn-piz-container .axn-piz-card")
            .map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage = false)
    }

    private var lastManga: SManga? = null
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val form = FormBody.Builder()
            .add("action", "axn_filter_obras")
            .add("posts_per_page", "21")
            .add("search", query)
            .add("paged", page.toString())
            .apply {
                filters.filterIsInstance<BoxList>()
                    .also { filterList ->
                        filterList.find { it.name.contains("ordem", ignoreCase = true) }
                            ?.state?.find(CheckBox::state)?.let {
                                if (it.id.isBlank()) return@let
                                add("letra", it.id)
                            }
                    }
                    .filterNot { it.name.contains("ordem", ignoreCase = true) }
                    .flatMap(BoxList::state)
                    .filter { it.state && it.id.isNotBlank() }
                    .forEach { filter -> add("terms[]", filter.id) }
            }
            .build()
        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", form)

        val mangas = response.asJsoup().select("a.axn-card").map(::mangaFromElement).toMutableList()
        val hasNextPage = mangas.isNotEmpty() && mangas.size > 1

        when {
            hasNextPage -> {
                lastManga = mangas.removeAt(mangas.lastIndex)
            }
            else -> {
                lastManga?.let { mangas += it }
            }
        }

        return MangasPage(mangas, hasNextPage = hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.filter(String::isNotEmpty).size != 2 || !baseUrl.endsWith(url.host, ignoreCase = true)) {
            return null
        }
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return fetchMangaUpdate(
            manga,
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val mangaUpdate = when {
            fetchDetails -> document.selectFirst("#axn-data")!!.data()
                .parseAs<MangaDto>().toSManga().apply {
                    setUrlWithoutDomain(document.location())
                }
            else -> manga
        }
        val chaptersUpdate = when {
            fetchChapters -> getChapterList(document)
            else -> chapters
        }

        return SMangaUpdate(mangaUpdate, chaptersUpdate)
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["link"]!!.string

    private suspend fun getChapterList(document: Document): List<SChapter> {
        val category = document.selectFirst("[data-categoria]")
            ?.attr("data-categoria")
            ?: return emptyList()

        val url = "$baseUrl/wp-json/axn/v1/chapters".toHttpUrl().newBuilder()
            .addPathSegment(category)

        val chapterList = mutableListOf<SChapter>()
        var page = 1
        do {
            url.setQueryParameter("page", (page++).toString())
            val response = client.get(url.build())
            val currentPage = response.getChapterList()
            chapterList += currentPage
        } while (currentPage.isNotEmpty())
        return chapterList.reversed()
    }

    private fun Response.getChapterList(): List<SChapter> = parseAs<List<ChapterDto>>().map(ChapterDto::toSChapter)
        .filter { it.name.contains(chapterNameSuffixRegex) }

    private val pageContainerSelector = ".spice-block-img-gallery, .wp-block-gallery, .spnc-entry-content, .leitor-cascata"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.selectFirst(pageContainerSelector)!!.select("img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    private class BoxList(title: String, values: List<BoxValue>) : Filter.Group<CheckBox>(title, values.map { CheckBox(it.name, it.id) })

    private class CheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/pesquisar").asJsoup()

        val filtersSelectors = setOf(
            "grp-alfabeto",
            "grp-demografia",
            "grp-classificacao",
            "grp-categorias",
            "grp-tags",
        )

        val options = filtersSelectors.mapNotNull { selector ->
            val title = document.selectFirst(".filter-section-title:has( + #$selector)")?.text()
                ?: return@mapNotNull null
            title to document.select("#$selector .axn-chip").map { element ->
                BoxValue(element.text(), element.attr("data-value"))
            }
        }

        return FilterOptionsDTO(options).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        if (data == null) return FilterList()

        val dto = data.parseAs<FilterOptionsDTO>()

        val filters: MutableList<Filter<out Any>> = mutableListOf()

        dto.options.forEachIndexed { index, (title, values) ->
            if (index != 0) {
                filters += Filter.Separator()
            }
            filters += BoxList(title, values)
        }
        return FilterList(filters)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h2, h3, .search-content")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    companion object {
        val supportsTypeSource = setOf("Mangá", "Manhwa", "Manhua")
        val chapterNameSuffixRegex = """Cap.tulo""".toRegex(RegexOption.IGNORE_CASE)
    }
}
