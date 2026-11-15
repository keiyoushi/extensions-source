package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
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
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("a.nextpostslink") != null)
    }

    // The site hashes its theme classes, so these use WordPress classes, structure and labels
    override val filterGenresSelector = "body"
    override fun archiveSelector() = "a:has(img.wp-post-image)"
    override val archiveUrlSelector = "a"

    override val mangaDetailsSelectorTitle = ".type-wp-manga h1"
    override val mangaDetailsSelectorStatus = "span:matchesOwn(^Estado$) + span"
    override val mangaDetailsSelectorDescription = "div:has(> p):has(+ button)"
    override val mangaDetailsSelectorThumbnail = "img.wp-post-image"
    override val mangaDetailsSelectorGenre = "div > a[href*=/manga-genre/]"
    override val seriesTypeSelector = "h1 span"
    override val altNameSelector = "span:matchesOwn(^Alternativo$) + span"

    override val pageListParseSelector = "img.wp-manga-chapter-img"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        element.attrOrNull("title")?.let { title = it }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("script[type=application/json]:containsData(mangaId)")!!.data()
        .parseAs<ChapterListDto>().items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
}
