package eu.kanade.tachiyomi.extension.zh.jiuermanhua

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class JiuerManhua : SinMH() {

    override val mobileUrl = "http://h5.92mh.com"

    override fun mangaDetailsParse(document: Document) = mangaDetailsParseDMZJStyle(document, hasBreadcrumb = false)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)
}
