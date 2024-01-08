package eu.kanade.tachiyomi.extension.zh.manhuadui

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Document

// This site blocks IP outside China
class YKMH : SinMH("优酷漫画", "http://www.ykmh.com") {
    override val id = 1637952806167036168
    override val mobileUrl = "http://wap.ykmh.com"

    override fun mangaDetailsParse(document: Document) = mangaDetailsParseDMZJStyle(document, hasBreadcrumb = false)

    override fun List<SChapter>.sortedDescending() = this
}
