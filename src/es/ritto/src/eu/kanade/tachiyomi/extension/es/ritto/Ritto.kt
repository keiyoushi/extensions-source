package eu.kanade.tachiyomi.extension.es.ritto

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

@Source
abstract class Ritto : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getCatalog(page) { addQueryParameter("orden", "vistas") }

    override suspend fun getLatestUpdates(page: Int) = getCatalog(page) { addQueryParameter("orden", "reciente") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getCatalog(page) {
        if (query.isNotBlank()) addQueryParameter("busqueda", query.trim())
        filters.filterIsInstance<UriPartFilter>().forEach { it.addTo(this) }
    }

    private suspend fun getCatalog(page: Int, parameters: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$baseUrl/catalogo".toHttpUrl().newBuilder()
            .apply(parameters)
            .addQueryParameter("pagina", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.workCards(), document.extractNextJs<PaginationDto>()?.hasNextPage() == true)
    }

    private fun Document.workCards() = select("a.ritto-work-card")
        .filterNot { card -> card.select("span").any { it.ownText() == NOVEL } }
        .map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        return when (url.pathSegments.first()) {
            "catalogo" -> getCatalog(page) {
                url.queryParameterNames.filter { it != "pagina" && it != "vista" }.forEach { name ->
                    url.queryParameterValues(name).forEach { addQueryParameter(name, it) }
                }
            }

            "autor" -> MangasPage(client.get(url, Fetch.DOCUMENT).asJsoup().workCards(), false)

            // A scan's page lists all its works at once
            "scan" -> {
                val mangas = client.get(url, Fetch.DOCUMENT).asJsoup().select("article.sx-work")
                    .filterNot { it.selectFirst(".sx-work__badge")?.ownText() == NOVEL }
                    .map { work ->
                        SManga.create().apply {
                            val link = work.selectFirst(".sx-work__title a")!!
                            setUrlWithoutDomain(link.absUrl("href"))
                            title = link.text()
                            thumbnail_url = work.selectFirst("img")?.absUrl("src")
                        }
                    }
                MangasPage(mangas, false)
            }

            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "obra") return null
        val manga = SManga.create().apply { this.url = "/obra/${segments[1]}" }
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // A missing work answers 200 with an "Obra no encontrada" page
        if (document.selectFirst("h1.rf-hero__title") == null || document.type() == NOVEL) return null
        return document.toSMangaDetails().apply {
            this.url = manga.url
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = document.toSMangaDetails(),
            chapters = document.extractNextJs<List<ChapterDto>>().orEmpty().flatMap { it.toSChapters() },
        )
    }

    private fun Document.toSMangaDetails() = SManga.create().apply {
        title = selectFirst("h1.rf-hero__title")!!.text()
        thumbnail_url = selectFirst(".rf-hero__cover img")?.absUrl("src")
        val info = select(".rf-info-row").associate { it.child(0).text() to it.child(1).text() }
        author = info["Autor"]
        artist = info["Artista"]
        genre = select(".rf-hero__tags a").joinToString { it.text() }.ifEmpty { null }
        status = when (selectFirst("a.rf-hero__estado")?.absUrl("href")?.toHttpUrlOrNull()?.queryParameter("estado")) {
            "EN_EMISION" -> SManga.ONGOING
            "FINALIZADO" -> SManga.COMPLETED
            "PAUSADO" -> SManga.ON_HIATUS
            "CANCELADO" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .info(type(), info["Año"])
            .description(selectFirst(".rf-hero__synopsis .whitespace-pre-wrap"), keepNewlines = true)
            .field("Scan", select(".rf-hero__scans a").map { it.text() })
            .altTitles(listOfNotNull(selectFirst(".rf-hero__alt")?.textOrNull()))
            .build()
    }

    // "Manga · Proyecto fansub"
    private fun Document.type() = selectFirst(".rf-hero__tipo")?.text()?.substringBefore(" · ")

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.reader-image")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        // Text novels, listed with the comics on the site
        private const val NOVEL = "Novela"
    }
}
