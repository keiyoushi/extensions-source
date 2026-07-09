package eu.kanade.tachiyomi.extension.tr.uzaymanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class UzayManga : UzayManga() {
    override val cdnUrl = "https://cdn-u.efsaneler2.can.re"
}
