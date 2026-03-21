package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDenizi : HttpSource() {
    override val name = "MangaDenizi"
    override val baseUrl = "https://www.mangadenizi.net"
    override val lang = "tr"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> JsonElement.parseAs(): T = json.decodeFromJsonElement(this)

    private fun rawProps(response: Response): JsonObject =
        response.asJsoup()
            .selectFirst("div#app")!!
            .attr("data-page")
            .let { json.parseToJsonElement(it).jsonObject["props"]!!.jsonObject }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?sort=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val props = rawProps(response)
        val paginated = props["manga"]!!.parseAs<MangaPaginated>()
        val mangas = paginated.data.map { it.toSManga() }
        return MangasPage(mangas, paginated.nextPageUrl != null)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga?sort=latest&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            "$baseUrl/manga".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build(),
            headers,
        )

    override fun searchMangaParse(response: Response): MangasPage =
        popularMangaParse(response)

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val props = rawProps(response)
        return props["manga"]!!.parseAs<MangaItem>().toSManga()
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request =
        GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val props = rawProps(response)
        val item = props["manga"]!!.parseAs<MangaItem>()
        return item.chapters.map { it.toSChapter(item.slug) }.reversed()
    }

    // ============================== Pages =================================

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val props = rawProps(response)
        val pages = props["pages"]!!.parseAs<List<PageItem>>()
        return pages.mapIndexed { idx, page -> Page(idx, imageUrl = page.imageUrl) }
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    // =========================== Helpers ==================================

    private fun MangaItem.toSManga() = SManga.create().apply {
        url = "/manga/$slug"
        title = this@toSManga.title
        thumbnail_url = coverUrl
        description = this@toSManga.description
        author = authors.firstOrNull()?.name
        genre = categories.joinToString { it.name }
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun ChapterItem.toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug"
        name = buildString {
            append("Bölüm $number")
            if (!title.isNullOrBlank()) append(": $title")
        }
        date_upload = dateFormat.tryParse(publishedAt.substringBefore("T"))
        chapter_number = number.toFloat()
    }

    companion object {
        val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}

fun SimpleDateFormat.tryParse(string: String): Long =
    runCatching { parse(string)?.time }.getOrNull() ?: 0L
