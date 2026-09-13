package eu.kanade.tachiyomi.extension.tr.ghosthentai

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class GhosToon : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
    override fun searchCardSelector() = archiveSelector()

    override fun parseChapterDate(date: String?) = 0L

    override fun parsePages(document: Document): List<Page> {
        val pageList = super.parsePages(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Inicie sesión en WebView para ver este capítulo")
        }
        return pageList
    }

    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"
}
