package eu.kanade.tachiyomi.extension.zh.manhuadui

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

// This site blocks IP outside China
@Source
abstract class YKMH : SinMH() {

    override fun mangaDetailsParse(document: Document) = mangaDetailsParseDMZJStyle(document, hasBreadcrumb = false)

    override fun List<SChapter>.sortedDescending() = this
}
