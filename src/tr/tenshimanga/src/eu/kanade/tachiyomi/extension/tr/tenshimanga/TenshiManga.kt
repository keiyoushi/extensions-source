package eu.kanade.tachiyomi.extension.tr.tenshimanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class TenshiManga : UzayManga() {
    override val cdnUrl = "https://cdn-t.efsaneler2.can.re"
}
