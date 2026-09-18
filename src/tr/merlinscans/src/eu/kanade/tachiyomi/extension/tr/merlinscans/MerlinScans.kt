package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class MerlinScans : InitManga() {

    override val popularUrlSlug = "seri-siralamasi"

    override val latestUrlSlug = "son-guncellenenler"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("h2 a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        // The description div also carries a hidden SEO block, only the paragraphs are the real synopsis
        description = document.select("div#manga-description > p")
            .map { it.text() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        document.selectFirst("span#comic-othername")?.text()?.let {
            description += "\n\nAlternatif Başlık: $it"
        }
        genre = document.select("div#genre-tags a[href*=/tur/]").joinToString { it.text() }
        author = document.infoValue("Yazar")
        artist = document.infoValue("Çizer")
    }

    // Info block is "Label: <a|span>value</a|span><br>" repeated, labels are bare text nodes
    private fun Document.infoValue(label: String): String? {
        val nodes = selectFirst("div.manga-info-details")?.childNodes() ?: return null
        val index = nodes.indexOfFirst { it is TextNode && it.text().trim() == "$label:" }
        if (index < 0) return null
        return nodes.drop(index + 1).firstOrNull { it is Element }?.let { (it as Element).text() }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", manga.url.trimEnd('/').substringAfterLast('/'))
            .addQueryParameter("_fields", "id,link")
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<List<MangaIdDto>>().first()
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("manga_id", manga.id.toString())
                .addQueryParameter("per_page", "50")
                .addQueryParameter("paged", page.toString())
                .build()
            val result = client.newCall(GET(url, headers)).execute().parseAs<ChapterListDto>()

            result.items.mapTo(chapters) { chapter ->
                SChapter.create().apply {
                    setUrlWithoutDomain("${manga.link}${chapter.slug}/")
                    name = "Bölüm ${chapter.number.toString().removeSuffix(".0")}"
                    if (chapter.title.isNotBlank()) {
                        name += " - ${chapter.title}"
                    }
                    date_upload = dateFormat.tryParseDateTime(chapter.createdAt, ZoneId.of("Europe/Istanbul"))
                }
            }
            page++
        } while (page <= result.totalPages)

        return chapters
    }

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("div#chapter-content div.lock-card") != null) {
            throw Exception("Kilitli bölüm, okumak için siteye giriş yapmanız gerekiyor")
        }
        return super.pageListParse(document)
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
