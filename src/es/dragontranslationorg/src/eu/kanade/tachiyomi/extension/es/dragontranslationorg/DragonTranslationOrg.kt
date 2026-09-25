package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("es"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val filterGenresSelector = ".filters"
    override fun archiveSelector() = "a.acard"
    override val archiveUrlSelector = "a"
    override val archiveTitleSelector = ".ac-t"

    override val mangaDetailsSelectorTitle = "div.hcol > .htitle"
    override val mangaDetailsSelectorStatus = "div.hcol > .htags > .htag--status"
    override val mangaDetailsSelectorDescription = "div#syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter__card > img"
    override val mangaDetailsSelectorGenre = "div.hcol > .hchips--genres > a.chip"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("script#mk-chapters-data")!!.data()
        .parseAs<ChapterListDto>().items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
}
