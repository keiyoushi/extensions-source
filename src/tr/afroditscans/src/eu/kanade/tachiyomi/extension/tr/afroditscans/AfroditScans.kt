package eu.kanade.tachiyomi.extension.tr.afroditscans

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class AfroditScans : UzayManga() {
    override val cdnUrl = "https://cdn-a.efsaneler2.can.re"
}
