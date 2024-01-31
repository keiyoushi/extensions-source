package eu.kanade.tachiyomi.extension.zh.jiuermanhua

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Document

class JiuerManhua : SinMH("92漫画", "http://www.92mh.com") {

    override fun mangaDetailsParse(document: Document) = mangaDetailsParseDMZJStyle(document, hasBreadcrumb = false)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)
}
