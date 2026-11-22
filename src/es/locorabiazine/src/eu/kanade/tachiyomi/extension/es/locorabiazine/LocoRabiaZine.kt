package eu.kanade.tachiyomi.extension.es.locorabiazine

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
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.Normalizer

@Source
abstract class LocoRabiaZine : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getGallery(GalleryFilter().path)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // The site has no search, so the titles of a gallery are matched here
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val terms = query.trim().normalized()
        val mangas = getGallery(filters.firstInstance<GalleryFilter>().path).mangas
        return MangasPage(mangas.filter { it.title.normalized().contains(terms) }, false)
    }

    private suspend fun getGallery(path: String): MangasPage {
        val document = client.get(pageUrl(path), Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.galleryMangas(), false)
    }

    private fun Document.galleryMangas(): List<SManga> = select("section.project-covers a.project-cover").map { cover ->
        SManga.create().apply {
            setUrlWithoutDomain(cover.absUrl("href"))
            title = cover.selectFirst(".title")!!.text().withoutStatus()
            thumbnail_url = cover.selectFirst("img.cover__img")?.absUrl("data-src")
        }
    }

    // Gallery links give their list, project links their series
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val slug = url.pathSegments.first()
        val document = client.get(pageUrl(slug), Fetch.DOCUMENT).asJsoup()

        val gallery = document.galleryMangas()
        if (gallery.isNotEmpty()) return MangasPage(gallery, false)

        val parts = document.parts()
        if (parts.isEmpty()) return MangasPage(emptyList(), false)
        val manga = SManga.create().apply {
            this.url = "/$slug"
            setDetails(document)
            // Project pages don't show the cover their gallery uses
            thumbnail_url = parts.first().images.first()
            initialized = true
        }
        return MangasPage(listOf(manga), false)
    }

    // Details and chapters come from the same project page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.setDetails(document)
        val chapterList = document.parts().mapIndexed { i, part ->
            SChapter.create().apply {
                url = "${manga.url}/${part.id}"
                name = "Parte ${i + 1}"
                date_upload = part.date
            }
        }
        return SMangaUpdate(manga, chapterList.reversed())
    }

    private fun SManga.setDetails(document: Document) {
        val name = document.selectFirst("header.page-header h1.title")!!.text()
        title = name.withoutStatus()
        status = when (STATUS_REGEX.find(name)?.groupValues?.get(1)) {
            null -> SManga.UNKNOWN
            "COMPLETA" -> SManga.COMPLETED
            "PAUSADA" -> SManga.ON_HIATUS
            // The day of the week the series comes out
            else -> SManga.ONGOING
        }
        update_strategy = if (status == SManga.COMPLETED) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        val intro = document.selectFirst("#project-modules > .project-module")?.takeIf { it.hasClass("project-module-text") }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("header.page-header p.description"))
            .description(intro?.selectFirst(".rich-text"))
            .build()
    }

    override fun getChapterUrl(chapter: SChapter): String = pageUrl(chapter.url.substringBeforeLast('/').removePrefix("/"))

    // The newest part of a series gets new pages every week, so they are read from the project page each time
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val id = chapter.url.substringAfterLast('/')
        val images = document.parts().firstOrNull { it.id == id }?.images.orEmpty()
        return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private class Part(val id: String, val date: Long, val images: List<String>)

    // Each block of images (a grid, a single image or a row of them) is a part of the series
    private fun Document.parts(): List<Part> = select("#project-modules > .project-module:not(.project-module-text)").mapNotNull { module ->
        val images = module.select(".js-lightbox[data-src], img.grid__item-image[data-src]").map { it.absUrl("data-src") }
        if (images.isEmpty()) return@mapNotNull null
        val id = module.attr("data-id")
        Part(
            // Single images have no id: their file name stands in for one
            id = id.ifEmpty { images.first().toHttpUrl().pathSegments.last().substringBefore('.') },
            // Grid ids start with their creation time in hexadecimal seconds, like MongoDB ids
            date = MODULE_TIME_REGEX.find(id)?.groupValues?.get(1)?.toLong(16)?.times(1000) ?: 0L,
            images = images,
        )
    }

    private fun pageUrl(slug: String): String = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).build().toString()

    private fun String.withoutStatus() = replace(STATUS_REGEX, "")

    private fun String.normalized() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS_REGEX, "")

    companion object {
        private val STATUS_REGEX = Regex("""^(LUNES|MARTES|MIÉRCOLES|JUEVES|VIERNES|SÁBADOS?|DOMINGOS?|COMPLETA|PAUSADA):\s*""")
        private val MODULE_TIME_REGEX = Regex("""^m([0-9a-f]{8})""")
        private val DIACRITICS_REGEX = Regex("""\p{Mn}+""")
    }
}
