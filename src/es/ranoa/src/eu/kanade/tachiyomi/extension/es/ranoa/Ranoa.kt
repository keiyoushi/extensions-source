package eu.kanade.tachiyomi.extension.es.ranoa

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Ranoa : KeiSource() {

    // A few works, all on one page
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(getWorks().obras.map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val works = getWorks().obras.filter { it.titulo.contains(query.trim(), ignoreCase = true) }
        return MangasPage(works.map { it.toSManga() }, false)
    }

    // The manga page has every work with its volumes and chapters; the novels are text
    private suspend fun getWorks(key: String? = null): CatalogDto = getCatalogPage(key).extractNextJs<CatalogDto>()!!

    private suspend fun getCatalogPage(key: String?): Document {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply { key?.let { addQueryParameter("obra", it) } }.build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
    }

    // A work's page, or one of its volumes or chapters; the main manga's links have no work in them
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.firstOrNull() != "manga") return MangasPage(emptyList(), false)
        val works = getWorks().obras
        val key = url.queryParameter("obra") ?: segments.getOrNull(1)?.takeIf { it != "tomo" && it != "leer" } ?: works.first().key.takeIf { segments.size > 1 }
        return MangasPage(works.filter { it.key == key }.map { it.toSManga() }, false)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga?obra=${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = getCatalogPage(manga.url)
        val work = document.extractNextJs<CatalogDto>()!!.obras.first { it.key == manga.url }
        val details = work.toSManga().apply {
            description = document.selectFirst("meta[name=description]")?.attr("content")
            status = when (work.contenido.find("data-publication-status")?.content) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        val chapterList = work.contenido.volumes()
            .flatMap { volume -> volume.find("chapters")!!.parseAs<List<ChapterDto>>().map { it.toSChapter(volume.find("href")!!.content) } }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .extractNextJs<ReaderDto>()!!
        .pages.mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }

    // The volumes are cards of the page, each with its link and its chapters
    private fun JsonElement.volumes(): List<JsonElement> = when (this) {
        is JsonObject -> if ("data-book-number" in this) listOf(this) else values.flatMap { it.volumes() }
        is JsonArray -> flatMap { it.volumes() }
        else -> emptyList()
    }

    private fun JsonElement.find(key: String): JsonElement? = when (this) {
        is JsonObject -> this[key] ?: values.firstNotNullOfOrNull { it.find(key) }
        is JsonArray -> firstNotNullOfOrNull { it.find(key) }
        else -> null
    }

    private val JsonElement.content get() = (this as JsonPrimitive).content
}
