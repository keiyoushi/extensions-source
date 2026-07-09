package eu.kanade.tachiyomi.extension.tr.mikrokosmosfansub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Response
import org.jsoup.Jsoup

@Source
abstract class MikrokosmosFansub : ZeistManga() {

    override val pageListSelector = "div.check-box script:containsData(content)"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.select(pageListSelector)
        val content = script.html().substringAfter("const content = `").substringBefore("`;")
        val images = Jsoup.parse(content).select("a")
        return images.select("img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }
}
