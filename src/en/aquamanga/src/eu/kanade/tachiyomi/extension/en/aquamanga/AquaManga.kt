package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class AquaManga : Madara() {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun popularMangaSelector() = ".aqua-archive-card"
    override val popularMangaUrlSelector = ".aqua-archive-card__title a"
    override val popularMangaUrlSelectorImg = ".aqua-archive-card__cover"
    override fun popularMangaNextPageSelector() = "a.next"

    private val pageEqualRegex = Regex("""Page (\d+) of \1""")

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        val hasNextPage = !document.title().contains(pageEqualRegex)
        return MangasPage(mangas, hasNextPage)
    }

    override val mangaDetailsSelectorTitle = ".aqua-series-info__title"
    override val mangaDetailsSelectorThumbnail = ".aqua-series-cover__img"
    override val mangaDetailsSelectorDescription = ".aqua-series-synopsis"
    override val mangaDetailsSelectorStatus = ".aqua-series-meta__status"
    override val mangaDetailsSelectorGenre = ".aqua-series-genre-pill"
    override val mangaDetailsSelectorAuthor = ".aqua-series-info__creator-value a"
    override val mangaDetailsSelectorArtist = ".aqua-series-info__creator-value a"

    override fun chapterListSelector() = ".aqua-ch-item"
    override val chapterUrlSuffix = ""

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("abs:href")
        name = element.selectFirst(".aqua-ch-item__name")?.text()!!
        element.selectFirst(".aqua-ch-item__time")?.text()?.let {
            date_upload = parseChapterDate(it)
        }
    }
}
