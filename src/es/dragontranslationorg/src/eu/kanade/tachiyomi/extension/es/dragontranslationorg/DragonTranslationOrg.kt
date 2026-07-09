package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaSelector() = "div#mkAgrid > a.acard"

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi > a.nextpostslink"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.selectFirst("div.ac-t")!!.ownText()
        element.selectFirst(popularMangaUrlSelectorImg)?.let {
            thumbnail_url = processThumbnail(imageFromElement(it), true)
        }
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override val mangaDetailsSelectorTitle = "div.hcol > .htitle"
    override val mangaDetailsSelectorStatus = "div.hcol > .htags > .htag--status"
    override val mangaDetailsSelectorDescription = "div#syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter__card > img"
    override val mangaDetailsSelectorGenre = "div.hcol > .hchips--genres > a.chip"

    override fun chapterListParse(response: Response): List<SChapter> {
        val scriptData = response.asJsoup().selectFirst("script#mk-chapters-data")!!.data()
        val dto = scriptData.parseAs<ChapterListDto>()
        return dto.items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
    }
}
