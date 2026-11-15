package eu.kanade.tachiyomi.extension.ar.paradisebl

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class ParadiseBL : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax

    override fun archiveSelector() = "article.mn-card"
    override val archiveUrlSelector = ".mn-card__title a"
    override fun nextPageSelector() = ".mn-pagination .next"
    override val mangaDetailsSelectorTitle = ".mn-hero__title"

    override fun Element.postId() = attr("data-id").takeIf(String::isNotBlank)
}
