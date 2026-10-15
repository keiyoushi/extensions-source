package eu.kanade.tachiyomi.extension.es.anzmanga

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import keiyoushi.annotation.Source

@Source
abstract class AnzManga : MMRCMS() {

    override val supportsAdvancedSearch = false
}
