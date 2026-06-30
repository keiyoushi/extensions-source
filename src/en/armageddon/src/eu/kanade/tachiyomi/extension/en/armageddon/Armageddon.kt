package eu.kanade.tachiyomi.extension.en.armageddon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Armageddon : MangaThemesia() {
    override val seriesTitleSelector = "h1.kdt8-left-title"

    override val seriesThumbnailSelector = ".kdt8-cover img"

    override val seriesDescriptionSelector = ".kdt8-synopsis"

    override val seriesGenreSelector = ".kdt8-genres a.kdt8-genre-tag"
}
