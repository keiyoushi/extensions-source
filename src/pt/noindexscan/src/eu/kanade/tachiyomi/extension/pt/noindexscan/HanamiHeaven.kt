package eu.kanade.tachiyomi.extension.pt.noindexscan

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class HanamiHeaven : MadaraNoAjax() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 2.seconds)

    override val mangaSubString = "todas-as-obras"
    override val orderQueryParameter = "ordem"
    override val searchQueryParameter = "busca"
    override fun nextPageSelector() = ".mch-catalogue__pages a.next"

    override fun archiveUrlBuilder(page: Int, order: String, path: String, query: String) = super.archiveUrlBuilder(1, order, path, query).apply {
        if (page > 1) addQueryParameter("pagina", page.toString())
    }

    override val completedStatus = super.completedStatus + "completa"
    override val hiatusStatus = super.hiatusStatus + "em espera"
    override val cancelledStatus = super.cancelledStatus + "cancelada"

    // Novels have text chapters, which the reader cannot show
    override fun archiveSelector() = "li.mch-catalogue-card:not(:has(.mch-catalogue-card__meta span:contains(Novel)))"
    override val archiveUrlSelector = ".mch-catalogue-card__title a"
    override fun Element.postId() = attr("data-work-id")

    override val mangaDetailsSelectorTitle = "#mch-work-title"
    override val mangaDetailsSelectorAuthor = metadataSelector("Autor", "a")
    override val mangaDetailsSelectorArtist = metadataSelector("Artista", "a")
    override val mangaDetailsSelectorStatus = ".mch-work__status"
    override val mangaDetailsSelectorDescription = "#mch-work-synopsis"
    override val mangaDetailsSelectorThumbnail = ".mch-work__cover img"
    override val mangaDetailsSelectorGenre = metadataSelector("Gêneros", "a")
    override val mangaDetailsSelectorTag = metadataSelector("Tags", "a")
    override val seriesTypeSelector = metadataSelector("Tipo", "dd")
    override val altNameSelector = metadataSelector("Nome alternativo", "dd")

    override fun getHomeUrl() = "$baseUrl/$mangaSubString/"

    override suspend fun getPopularManga(page: Int) = archivePage(page, "rating")

    override suspend fun getLatestUpdates(page: Int) = archivePage(page, "")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = getHomeUrl().toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
        }.build()
        return archivePage(page, "", url.toString(), query)
    }

    // Chapter pages have no post id
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val isChapter = segments.size > 2 && segments[0] == "manga"
        return super.getMangaByUrl(if (isChapter) url.newBuilder().encodedPath("/manga/${segments[1]}/").build() else url)
    }

    // Genre links carry the slug in ?genero=
    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val genres = document.select(mangaDetailsSelectorGenre).mapNotNull { link ->
            link.attr("abs:href").toHttpUrl().queryParameter("genero")?.let { GenreRoute(link.text(), it, "/$mangaSubString/?genero[]=$it") }
        }
        memo = JsonObject(memo + ("genres" to genres.toJsonElement()))
    }

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        date_upload = Instant.tryParse(element.selectFirst("time")?.attr("datetime"))
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(getHomeUrl(), Fetch.DOCUMENT).asJsoup()
        return document.select("input[name=genero[]]").map {
            GenreRoute(it.parent()!!.text(), it.attr("value"), "")
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreRoute>>().orEmpty()
        return FilterList(
            buildList {
                add(SelectFilter("Ordenar por", "ordem", SORT_OPTIONS))
                add(SelectFilter("Tipo", "tipo", TYPE_OPTIONS))
                add(SelectFilter("Status", "status", STATUS_OPTIONS))
                add(SelectFilter("Quantidade de capítulos", "capitulos", CHAPTER_COUNT_OPTIONS))
                add(YearFilter("Ano de lançamento"))
                if (genres.isNotEmpty()) add(GenreFilter("Gêneros", genres))
            },
        )
    }

    private fun metadataSelector(label: String, value: String) = ".mch-work__metadata div:has(> dt:contains($label)) $value"
}
