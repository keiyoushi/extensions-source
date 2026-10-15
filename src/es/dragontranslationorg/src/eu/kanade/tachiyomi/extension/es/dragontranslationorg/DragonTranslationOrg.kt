package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("es"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val filterGenresSelector = ".filters"
    override fun archiveSelector() = "a:has(img.ac-cover)"
    override val archiveUrlSelector = "a"

    override val mangaDetailsSelectorTitle = "div.hero h1"
    override val mangaDetailsSelectorStatus = "div.htags > span.htag"
    override val mangaDetailsSelectorDescription = "div.syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter img"
    override val mangaDetailsSelectorGenre = "div.hchips a.chip"

    override val pageListParseSelector = "div.reading-content img.wp-manga-chapter-img"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        element.attr("title").takeIf(String::isNotBlank)?.let { title = it }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("#mkChapters script[type=application/json]")!!.data()
        .parseAs<ChapterListDto>().items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
}
