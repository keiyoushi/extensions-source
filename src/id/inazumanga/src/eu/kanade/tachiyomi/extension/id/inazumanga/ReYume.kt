package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class ReYume : ZeistManga("ReYume", "https://www.re-yume.my.id", "id") {

    override val popularMangaSelector = ".pop-card"
    override val popularMangaSelectorTitle = "h4 a"
    override val popularMangaSelectorUrl = "h4 a"

    override val mangaDetailsSelector = "#Blog1"
    override val mangaDetailsSelectorDescription = "#synopsis p"
    override val mangaDetailsSelectorGenres = "#append-info .col-span-2 a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "#extra-info dl:has(dt:contains(Author)) dd"
    override val mangaDetailsSelectorArtist = "#extra-info dl:has(dt:contains(Artist)) dd"
    override val mangaDetailsSelectorAltName = "#extra-info dl:has(dt:contains(Alternative)) dd"
    override val mangaDetailsSelectorStatus = "span[data-bg]"
    override val mangaDetailsSelectorInfo = "#append-info > div"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = ".separator"
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val textArea = document.selectFirst("textarea#zeist-raw-data")?.text().orEmpty()

        val images = response.asJsoup(textArea).select(pageListSelector)
        return images.select("img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }
}
