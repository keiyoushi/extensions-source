package eu.kanade.tachiyomi.extension.en.lhtranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class LHTranslation : Madara() {
    override val useNewChapterEndpoint = true
}
