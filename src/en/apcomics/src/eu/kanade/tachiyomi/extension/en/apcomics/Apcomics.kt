package eu.kanade.tachiyomi.extension.en.apcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Apcomics : Madara() {
    override val useNewChapterEndpoint: Boolean = true
}
