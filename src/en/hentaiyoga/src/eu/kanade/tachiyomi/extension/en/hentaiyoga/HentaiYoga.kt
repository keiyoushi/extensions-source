package eu.kanade.tachiyomi.extension.en.hentaiyoga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiYoga : KeiSource() {

    // The site lists series by update only
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl("manga", null, null, page))

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
        val url = listUrl(typeFilter?.path ?: "manga", typeFilter?.type, query.trim().takeIf(String::isNotEmpty), page)
        return getMangaList(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.first() !in URL_TYPES) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "genre" } ?: return null
        val manga = SManga.create().apply { this.url = "/${url.pathSegments.first()}/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select("ul.manga-chapters-list a.manga-chapter-list-link").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".manga-chapter-list-title")!!.text()
                date_upload = DATE_FORMAT.tryParseDate(element.selectFirst(".manga-chapter-list-meta")?.text(), ZoneOffset.UTC)
            }
        }
        return SMangaUpdate(parseDetails(document, manga.url, chapterList.size), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.chapter-image")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    private fun parseDetails(document: Document, path: String, chapterCount: Int): SManga = SManga.create().apply {
        url = path
        title = document.selectFirst(".manga-title-section h1")!!.text()
        thumbnail_url = document.selectFirst(".manga-thumbnail-large img")?.absUrl("src")
        author = document.info("author")
        artist = document.info("artist")
        genre = document.select("dl.manhwa-info dd.manhwa-genres a").eachText().joinToString().ifEmpty { null }
        status = when (document.info("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // Manga galleries have a single "Oneshot" chapter
        if (status == SManga.COMPLETED || (path.startsWith("/manga/") && chapterCount == 1)) {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".manga-description")?.textOrNull())
            .field("Origin", document.info("Origin"))
            .field("Color", document.info("Color"))
            .field("Censorship", document.info("Censorship"))
            .build()
    }

    private fun Document.info(label: String): String? = selectFirst("dl.manhwa-info dt:matchesOwn((?i)$label) + dd")?.textOrNull()

    private fun listUrl(path: String, type: String?, query: String?, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(path)
        .apply {
            type?.let { addQueryParameter("type", it) }
            query?.let { addQueryParameter("s", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }
        .build()

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("section.gallery-grid-section article.mcard").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a.mcard-title-link")!!.absUrl("href"))
                title = card.selectFirst(".mcard-title")!!.text()
                // Cards ask the image service for a 400 px copy of the cover
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.toHttpUrl()?.newBuilder()?.query(null)?.build()?.toString()
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    companion object {
        private val URL_TYPES = setOf("manhwa", "manga")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    }
}
