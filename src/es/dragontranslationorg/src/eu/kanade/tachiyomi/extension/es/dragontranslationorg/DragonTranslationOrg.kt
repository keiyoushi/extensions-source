package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("es"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/$mangaSubString/".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("m_orderby", "latest")
        }.build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("a.nextpostslink") != null)
    }

    override val filterGenresSelector = ".filters"
    override fun archiveSelector() = "a:has(> div.ac-img)"
    override val archiveUrlSelector = "a"

    override val mangaDetailsSelectorTitle = "div.hero__in h1"
    override val mangaDetailsSelectorStatus = "div.sir:has(span.l:contains(Estado)) span.v"
    override val mangaDetailsSelectorDescription = "div.hero__in div.clamp"
    override val mangaDetailsSelectorThumbnail = "div.hero__in img.wp-post-image"
    override val mangaDetailsSelectorGenre = "div.hero__in a[href*=/manga-genre/]"
    override val seriesTypeSelector = "div.hero__in h1 span.manga-title-badges"
    override val altNameSelector = "div.sir:has(span.l:contains(Alternativo)) span.v"

    override val pageListParseSelector = "div.reading-content img.wp-manga-chapter-img"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        element.attr("title").takeIf(String::isNotBlank)?.let { title = it }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("div.mk-chapters script[type=application/json]")!!.data()
        .parseAs<ChapterListDto>().items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
}
