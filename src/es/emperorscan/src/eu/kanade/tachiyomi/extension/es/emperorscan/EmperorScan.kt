package eu.kanade.tachiyomi.extension.es.emperorscan

import eu.kanade.tachiyomi.multisrc.imperiomanhwa.ImperioManhwa
import keiyoushi.annotation.Source

@Source
abstract class EmperorScan : ImperioManhwa() {
    override val searchPath = "buscar"
}
