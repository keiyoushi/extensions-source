package eu.kanade.tachiyomi.extension.pt.yomumangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class YomuMangas : HttpSource() {

    override val name = "Yomu Mangás"
    override val baseUrl = "https://yomumangas.com"
    private val apiUrl = "https://api.yomumangas.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("main[class*=page_Container] > div[class*=styles_Container]:nth-child(2) [class*=styles_Card]").mapNotNull {
            val a = it.selectFirst("a[href^=/mangas/]") ?: return@mapNotNull null
            val url = a.attr("abs:href").toHttpUrl()
            val id = url.pathSegments.getOrNull(1) ?: return@mapNotNull null
            val slug = url.pathSegments.getOrNull(2) ?: return@mapNotNull null
            val title = it.selectFirst("h3")?.text()
            if (title.isNullOrEmpty()) return@mapNotNull null

            SManga.create().apply {
                this.url = "$id#$slug"
                this.title = title
                thumbnail_url = a.selectFirst("img")?.attr("abs:src")?.replaceB2Uri()
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("type", filter.toUriPart())
                is StatusFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("status", filter.toUriPart())
                is NsfwFilter -> if (filter.toUriPart().isNotEmpty()) url.addQueryParameter("nsfw", filter.toUriPart())
                is GenreFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        url.addQueryParameter("genres", selected.joinToString(","))
                    }
                }
                is TagFilter -> {
                    val selected = filter.state.filter { it.state }.map { it.id }
                    if (selected.isNotEmpty()) {
                        url.addQueryParameter("tags", selected.joinToString(","))
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponse>()
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(
            dto.mangas.map { it.toSManga() },
            page < dto.pages,
        )
    }

    // ============================== Details ==============================
    override fun getMangaUrl(manga: SManga): String {
        val (id, slug) = manga.url.split("#", limit = 2)
        return "$baseUrl/mangas/$id/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val (id, _) = manga.url.split("#", limit = 2)
        return GET("$apiUrl/mangas/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsResponse>().manga.toSManga()

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val (id, slug) = manga.url.split("#", limit = 2)
        return GET("$apiUrl/mangas/$id/chapters#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.fragment ?: throw Exception("Slug not found")
        val mangaId = response.request.url.pathSegments.dropLast(1).last()
        val dto = response.parseAs<ChaptersResponse>()
        return dto.chapters.map { it.toSChapter(mangaId, slug, dateFormat) }.reversed()
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val pages = URI_REGEX.findAll(html).mapIndexed { index, matchResult ->
            Page(index, imageUrl = matchResult.value.replaceB2Uri())
        }.toList()

        if (pages.isEmpty()) {
            throw Exception("Nenhuma página encontrada. O layout do site pode ter mudado.")
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        NsfwFilter(),
        Filter.Separator(),
        GenreFilter(getGenresList()),
        Filter.Separator(),
        TagFilter(getTagsList()),
    )

    // ============================= Utilities =============================
    companion object {
        private val URI_REGEX = """b2://chapters/[^"\\]+""".toRegex()
    }
}
