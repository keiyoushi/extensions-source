package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class AquaManga : MadaraNoAjax() {
    override val supportsPostId = false

    override fun archiveSelector() = ".aqua-archive-card"
    override val archiveUrlSelector = ".aqua-archive-card__title a"
    override fun nextPageSelector() = "a.next"

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
