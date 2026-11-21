package eu.kanade.tachiyomi.extension.es.lhentaiunoriginal

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.int
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class LHentaiUnoriginal : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getListing("popular", page)

    override suspend fun getLatestUpdates(page: Int) = getListing("new", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                if (page > 1) addPathSegment("page").addPathSegment(page.toString())
                addQueryParameter("q", query.trim())
                addQueryParameter("lang", "es")
            }.build()
            return parseListing(client.get(url, Fetch.DOCUMENT).asJsoup())
        }

        val tag = filters.firstInstanceOrNull<TagFilter>()?.slug.orEmpty()
        val artist = filters.firstInstanceOrNull<ArtistFilter>()?.slug.orEmpty()
        val path = when {
            tag.isNotEmpty() -> "tag/$tag"
            artist.isNotEmpty() -> "artist/$artist"
            else -> filters.firstInstanceOrNull<CategoryFilter>()?.path ?: "browse"
        }
        return getListing(path, page)
    }

    // Tag, artist and category links open their listing
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        return when (segments[0]) {
            "g" -> MangasPage(listOfNotNull(getMangaByUrl(url)), false)
            "tag", "artist" -> segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?.let { getListing("${segments[0]}/$it", page) }
                ?: MangasPage(emptyList(), false)
            "search" -> getSearchMangaList(page, url.queryParameter("q").orEmpty(), FilterList())
            in LISTINGS -> getListing(segments[0], page)
            else -> MangasPage(emptyList(), false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "g") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        return getMangaUpdate(SManga.create().apply { this.url = "/g/$id" }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        }.build()
        return parseListing(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseListing(document: Document): MangasPage {
        val mangas = document.select("a.grid-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst("strong")!!.text()
                thumbnail_url = card.selectFirst("img.cover__image")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("nav.catalog-pagination > a:last-child") != null)
    }

    // The pages are stored under the IMHentai id of the gallery (sourceId)
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val id = manga.url.substringAfterLast('/')
        val gallery = document.extractNextJs<GalleryDto> { it is JsonObject && "sourceId" in it && it.getStringOrNull("id") == id }!!

        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Galería"
            memo = buildJsonObject {
                put("source", gallery.sourceId)
                put("pages", gallery.pages)
            }
        }
        val details = gallery.toSManga(baseUrl, document.selectFirst(".detail-alternate")?.text()).apply { url = manga.url }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val source = chapter.memo["source"]!!.string
        return (1..chapter.memo["pages"]!!.int).map { number ->
            val imageUrl = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("api/media")
                .addPathSegment(source)
                .addPathSegment("$number.jpg")
                .build()
            Page(number - 1, imageUrl = imageUrl.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val LISTINGS = setOf("browse", "new", "popular", "top", "new-manga", "manga", "doujinshi", "western", "image-set", "game-cg", "artist-cg")
    }
}
