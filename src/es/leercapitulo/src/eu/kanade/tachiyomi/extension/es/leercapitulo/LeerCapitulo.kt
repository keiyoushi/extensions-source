package eu.kanade.tachiyomi.extension.es.leercapitulo

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : KeiSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 3.seconds) { it.host == baseUrlHost }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup().select(".lc-slide").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-slide-name")!!.absUrl("href"))
                title = element.selectFirst("a.lc-slide-name")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl).asJsoup().select("article.lc-release").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-release-title")!!.absUrl("href"))
                title = element.selectFirst("a.lc-release-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query)
            addUriPart("genre", filters.firstInstanceOrNull<GenreFilter>())
            addUriPart("theme", filters.firstInstanceOrNull<ThemeFilter>())
            addUriPart("type", filters.firstInstanceOrNull<TypeFilter>())
            addUriPart("status", filters.firstInstanceOrNull<StatusFilter>())
            addUriPart("sort", filters.firstInstanceOrNull<SortFilter>())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("article.lc-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.lc-card-name")!!.absUrl("href"))
                title = element.selectFirst("a.lc-card-name")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.selectFirst("a.page-link[rel=next]") != null)
    }

    private fun HttpUrl.Builder.addUriPart(name: String, filter: UriPartFilter?) {
        filter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter(name, it) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Los filtros se pueden combinar con la búsqueda por texto."),
        GenreFilter(),
        ThemeFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost || url.pathSegments.firstOrNull() != "manga" || url.pathSize < 3) return null

        val response = client.get(baseUrl + url.encodedPath)
        // Follows the site's redirect to the canonical slug.
        val path = response.request.url.encodedPath
        return response.asJsoup().mangaDetails().apply { this.url = path }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(document.mangaDetails(), document.chapterList())
    }

    private fun Document.mangaDetails() = SManga.create().apply {
        title = selectFirst("h1")!!.text()
        thumbnail_url = selectFirst(".lc-cover-lg img")?.absUrl("src")

        // The first entry repeats the main title.
        val altNames = selectFirst("h1 + p.lc-muted")?.text()
            ?.split(" · ")?.drop(1)?.joinToString(" · ")
        description = buildString {
            selectFirst("#sinopsis p")?.wholeText()?.trim()?.takeIf { it.isNotEmpty() }?.let(::append)
            if (!altNames.isNullOrEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alt name(s): ")
                append(altNames)
            }
        }

        genre = select("a.badge[href^='/manga/?genre='], a.badge[href^='/manga/?theme=']")
            .joinToString { it.text() }
        author = fact("Autor")
        artist = fact("Dibujo")
        status = fact("Estado").toStatus()
    }

    private fun Document.fact(label: String): String? = select(".lc-facts li")
        .firstOrNull { it.selectFirst(".k")?.text() == label }
        ?.children()?.last()?.text()

    private fun Document.chapterList() = select("a.lc-chapter-row").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".n")!!.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".d")?.text(), ZoneOffset.UTC)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url).asJsoup()
        .select("#lcPages img[data-src]")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-src")) }

    private fun String?.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Paused" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
