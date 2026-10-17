package eu.kanade.tachiyomi.extension.id.themanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class TheManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    // =============================== Popular ================================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/?q=&sort=popular&page=$page").asJsoup()
        return mangaListParse(document)
    }

    // =============================== Latest =================================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/?q=&sort=latest_update&page=$page").asJsoup()
        return mangaListParse(document)
    }

    // =============================== Search =================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/explore".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())

            filters.forEach { (it as? UrlFilter)?.addToUrl(this) }
        }.build()

        val document = client.get(url).asJsoup()
        return mangaListParse(document)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("a.card, a.manga-card").mapNotNull { element ->
            val titleText = element.selectFirst(".card-title")?.text() ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = titleText
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(".explore-pagination__btn[rel=next], a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga).toHttpUrl().newBuilder()
            .addQueryParameter("all", "1")
            .build()
        val document = client.get(url).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { this.url = manga.url },
            chapterListParse(document),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null

        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "manga") return null

        val slug = segments[1]
        if (slug.isEmpty()) return null

        val mangaUrl = "/manga/$slug"
        val requestUrl = getMangaUrl(SManga.create().apply { this.url = mangaUrl }).toHttpUrl().newBuilder()
            .addQueryParameter("all", "1")
            .build()
        val document = client.get(requestUrl).asJsoup()
        if (document.selectFirst(".hero-title") == null) return null

        return mangaDetailsParse(document).apply {
            this.url = mangaUrl
            initialized = true
        }
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val titleText = document.selectFirst(".hero-title")?.text() ?: throw Exception("Missing title")
        title = titleText
        author = document.meta("Author")
        artist = document.meta("Artist")
        description = document.selectFirst(".synopsis-text")?.text()
        thumbnail_url = document.selectFirst(".hero-cover img")?.absUrl("src")

        status = when (document.selectFirst(".hero-status-badge")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = document.select(".meta-pill-row .meta-pill")
            .map { it.text() }
            .toMutableList()

        document.meta("Type")?.takeIf { it.isNotBlank() }?.let(genres::add)

        genre = genres.joinToString()
    }

    // ============================== Chapters ==============================
    private fun chapterListParse(document: Document): List<SChapter> = document.select(".chapter-row").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("data-href"))
            name = element.selectFirst(".chapter-title")?.text() ?: throw Exception("Missing chapter name")
            date_upload = Instant.tryParse(element.selectFirst("[data-local-time]")?.attr("data-local-time"))
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = if (!chapter.url.contains("/chapter/")) {
            // Support old Madara URLs
            val segments = chapter.url.toHttpUrl().pathSegments
            val mangaSlug = segments[1]
            val number = segments[2].removePrefix("chapter-").replace("-", ".")
            val dotIndex = number.indexOf('.')
            val formatted = if (dotIndex >= 0) {
                number.padEnd(dotIndex + 3, '0')
            } else {
                "$number.00"
            }

            "$baseUrl/manga/$mangaSlug/chapter/$formatted"
        } else {
            getChapterUrl(chapter)
        }

        val document = client.get(url).asJsoup()

        return document.select("img.page-img").mapIndexed { idx, image ->
            Page(idx, imageUrl = image.absUrl("src"))
        }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        StatusFilter(),
        GenreFilter(),
        Filter.Separator(),
        OtherFilterGroup(),
    )

    // ============================== Utils ===============================
    private fun Document.meta(label: String): String? = selectFirst(".meta-item-label:matchesOwn(^$label$) + .meta-item-value")?.text()
}
