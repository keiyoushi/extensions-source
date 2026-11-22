package eu.kanade.tachiyomi.extension.pt.blitztoons

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class BlitzToons : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(catalogUrl("populares", page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(catalogUrl("recentes", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/obras".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<QueryFilter>().forEach { filter ->
                filter.value?.let { addQueryParameter(filter.param, it) }
            }
            addQueryParameter("p", page.toString())
        }.build()
        return getMangaList(url)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSiteUrl() || url.pathSegments != listOf("obras")) return super.getMangasByUrl(url, page)
        val listUrl = "$baseUrl/obras".toHttpUrl().newBuilder()
            .encodedQuery(url.encodedQuery)
            .setQueryParameter("p", page.toString())
            .build()
        return getMangaList(listUrl)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (!url.isSiteUrl() || segments.size < 2 || segments[0] !in setOf("obra", "ler") || segments[1].isEmpty()) return null
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("[data-pages-wrap] img.page")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/obras", Fetch.DOCUMENT).asJsoup()
        .select(".filter-gen a.chip[href*=genero=]")
        .map { it.text() to it.absUrl("href").toHttpUrl().queryParameter("genero")!! }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<Pair<String, String>>>())

    private fun catalogUrl(order: String, page: Int) = "$baseUrl/obras".toHttpUrl().newBuilder()
        .addQueryParameter("ordem", order)
        .addQueryParameter("p", page.toString())
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.card").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("nav.pager a:containsOwn(Próxima)") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst(".card-title a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = selectFirst(".card-cover img")?.absUrl("src")
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst(".sbanner-txt h1")!!.text()
        thumbnail_url = selectFirst("img.sbanner-cover")?.absUrl("src")
        genre = select(".sgenres span").mapNotNull { it.textOrNull() }.distinct().joinToString().ifEmpty { null }
        status = when (selectFirst(".smeta div:has(dt:containsOwn(Status)) dd")?.text()?.lowercase()) {
            "em lançamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        val rating = selectFirst(".sstat:has(span:containsOwn(notas))")
        description = MarkdownDescription.Builder()
            .rating(
                rating?.selectFirst("strong")?.text()?.removePrefix("★")?.trim()?.toDoubleOrNull(),
                votes = rating?.selectFirst("span")?.text()?.substringBefore(" ")?.toIntOrNull(),
            )
            .info(selectFirst(".sbanner-txt .pill")?.text())
            .description(selectFirst(".syn"), keepNewlines = true)
            .altTitles(listOfNotNull(selectFirst(".sbanner-txt .salt")?.textOrNull()))
            .build()
    }

    private fun Document.toSChapters(): List<SChapter> = select("ul.chaplist li.chap").map { item ->
        SChapter.create().apply {
            val link = item.selectFirst("a.chap-alvo")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst(".chap-nome")!!.text()
            chapter_number = item.attr("data-num").toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(item.selectFirst("time[datetime]")?.attr("datetime"))
        }
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host
}
