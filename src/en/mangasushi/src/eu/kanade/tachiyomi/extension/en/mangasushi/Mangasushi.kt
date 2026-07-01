package eu.kanade.tachiyomi.extension.en.mangasushi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Mangasushi : Madara() {
    override val useNewChapterEndpoint: Boolean = true
}
