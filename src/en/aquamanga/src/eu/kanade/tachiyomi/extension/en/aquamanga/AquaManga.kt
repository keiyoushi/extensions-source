package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class AquaManga : MadaraNoAjax() {

    override fun archiveSelector() = ".aqua-archive-card"
    override val archiveUrlSelector = ".aqua-archive-card__title a"
    override fun nextPageSelector() = "a.next"

    // No postId
    override fun Element.postId() = "dummy"
    override fun mangaId(manga: SManga) = ""
    override fun parseArchive(document: Document) = super.parseArchive(document)
        .map {
            it.apply {
                url = memoPath(it)!!
            }
        }

    override val mangaDetailsSelectorTitle = ".aqua-series-info__title"
    override val mangaDetailsSelectorThumbnail = ".aqua-series-cover__img"
    override val mangaDetailsSelectorDescription = ".aqua-series-synopsis"
    override val mangaDetailsSelectorStatus = ".aqua-series-meta__status"
    override val mangaDetailsSelectorGenre = ".aqua-series-genre-pill"
    override val mangaDetailsSelectorAuthor = ".aqua-series-info__creator-value a"
    override val mangaDetailsSelectorArtist = ".aqua-series-info__creator-value a"

    override fun chapterListSelector() = ".aqua-ch-item"

    override val chapterUrlSelector = "a"
    override val chapterNameSelector = ".aqua-ch-item__name"
    override val chapterDateSelector = ".aqua-ch-item__time"
}
