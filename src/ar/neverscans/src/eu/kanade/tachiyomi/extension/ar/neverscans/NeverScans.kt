package eu.kanade.tachiyomi.extension.ar.neverscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class NeverScans : HttpSource() {

    override val name = "NeverScans"

    override val lang = "ar"

    override val baseUrl = "https://neverscans.com"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("a[href^=/manga/]").mapNotNull { el ->
            val href = el.attr("href")
            val slug = href.removePrefix("/manga/")
            if (slug.isBlank() || slug.contains("/") || slug.contains("?")) return@mapNotNull null
            val img = el.select("img").firstOrNull()
            val title = img?.attr("alt")?.trim()?.ifBlank { null }
                ?: slug.replace("-", " ").replaceFirstChar { it.uppercase() }
            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                thumbnail_url = img?.attr("abs:src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val html = doc.outerHtml()
        val scriptPattern = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val namePattern = Regex(""""name"\s*:\s*"([^"]+)"""")
        val descPattern = Regex(""""description"\s*:\s*"([^"]+)"""")
        val imgPattern = Regex(""""image"\s*:\s*"([^"]+)" """)

        var title = ""
        var description = ""
        var thumbnail = ""

        for (match in scriptPattern.findAll(html)) {
            val content = match.groupValues[1]
            if (!content.contains("@type")) continue
            if (!content.contains("Book") && !content.contains("CreativeWorkSeries")) continue

            namePattern.find(content)?.let { title = it.groupValues[1] }
            descPattern.find(content)?.let { description = it.groupValues[1] }
            imgPattern.find(content)?.let { thumbnail = it.groupValues[1] }
            break
        }

        if (title.isBlank()) {
            title = doc.select("h1").text()
        }

        return SManga.create().apply {
            this.title = title.ifBlank { return@apply }
            this.description = description
            this.thumbnail_url = thumbnail
            url = response.request.url.encodedPath
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.extractNextJs<ChapterListDto>()
            ?: return emptyList()

        return data.chapters.map { chapter ->
            SChapter.create().apply {
                url = chapter.url
                name = chapter.name
                chapter_number = parseChapterNumber(chapter.url)
            }
        }.sortedByDescending { it.chapter_number }
    }

    private fun parseChapterNumber(url: String): Float {
        val path = url.substringAfterLast("/")
        val numStr = path
            .removePrefix("chapter-")
            .replace(".0", "")
        return numStr.toFloatOrNull() ?: 0f
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<PageListDto>()

        if (data != null) {
            return data.image.mapIndexed { index, url ->
                Page(index, imageUrl = "$baseUrl$url")
            }
        }

        return emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

@Serializable
class ChapterListDto(
    @SerialName("chapters") private val _chapters: List<ChapterItemDto> = emptyList(),
) {
    val chapters: List<ChapterItemDto> get() = _chapters
}

@Serializable
class ChapterItemDto(
    @SerialName("url") val url: String = "",
    @SerialName("name") val name: String = "",
)

@Serializable
class PageListDto(
    @SerialName("image") private val _image: List<String> = emptyList(),
) {
    val image: List<String> get() = _image
}
