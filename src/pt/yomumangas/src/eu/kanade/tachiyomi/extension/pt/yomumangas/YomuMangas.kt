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
        // Selects the "Novos capítulos" section on the homepage
        val mangas = document.select("main.page_Container__dFHb5 > div.styles_Container__7PC4t:nth-child(2) div.styles_Card__jN8og").mapNotNull {
            val a = it.selectFirst("a[href^=/mangas/]") ?: return@mapNotNull null
            val href = a.attr("href")
            val parts = href.trim('/').split("/")
            val id = parts.getOrNull(1) ?: return@mapNotNull null
            val slug = parts.getOrNull(2) ?: return@mapNotNull null
            val title = it.selectFirst("h3.styles_Text__Lyxq9")?.text()
            if (title.isNullOrEmpty()) return@mapNotNull null

            SManga.create().apply {
                url = "$id#$slug"
                this.title = title
                thumbnail_url = it.selectFirst("img.styles_Cover__A3yb5")?.attr("abs:src")
                    ?.replace("b2://", "https://b2.yomumangas.com/")
            }
        }
        return MangasPage(mangas, false) // The homepage does not have pagination
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
        val id = manga.url.substringBefore("#")
        val slug = manga.url.substringAfter("#")
        return "$baseUrl/mangas/$id/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringBefore("#")
        return GET("$apiUrl/mangas/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsResponse>().manga.toSManga()

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringBefore("#")
        val slug = manga.url.substringAfter("#")
        return GET("$apiUrl/mangas/$id/chapters#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.fragment ?: throw Exception("Slug not found")
        val mangaId = response.request.url.pathSegments.dropLast(1).last()
        val dto = response.parseAs<ChaptersResponse>()
        // API returns oldest chapters first, so we reverse it to respect source order
        return dto.chapters.map { it.toSChapter(mangaId, slug, dateFormat) }.reversed()
    }

    // =============================== Pages ===============================
    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        val pages = URI_REGEX.findAll(html).mapIndexed { index, matchResult ->
            val uri = matchResult.groupValues[1]
            Page(index, imageUrl = uri.replace("b2://", "https://b2.yomumangas.com/"))
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
        // Matches internal backblaze chapter payloads directly without relying on JSON key structure
        // e.g., b2://chapters/6/326-18540/78d058b2.avif
        private val URI_REGEX = """(b2://chapters/[^"\\]+)""".toRegex()
    }
}
