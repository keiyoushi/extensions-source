package eu.kanade.tachiyomi.extension.tr.mikrokosmosfansub

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import org.jsoup.nodes.Document

@Source
abstract class MikrokosmosFansub : ZeistManga() {

    override val pageListSelector = ":root"

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("div.check-box script:containsData(content)")
        val content = script.html().substringAfter("const content = `").substringBefore("`;")
        return super.pageListParse(content.asJsoup(baseUrl))
    }
}
