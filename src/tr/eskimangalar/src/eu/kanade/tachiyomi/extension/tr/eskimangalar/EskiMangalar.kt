package eu.kanade.tachiyomi.extension.tr.eskimangalar

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class EskiMangalar : UzayManga() {
    override val cdnUrl = "https://cdn-es.efsaneler2.can.re"
}
