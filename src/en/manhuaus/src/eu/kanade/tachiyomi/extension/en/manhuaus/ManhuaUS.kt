package eu.kanade.tachiyomi.extension.en.manhuaus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaUS : Madara() {

    override val useNewChapterEndpoint: Boolean = true

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
