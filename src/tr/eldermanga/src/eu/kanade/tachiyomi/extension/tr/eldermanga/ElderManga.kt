package eu.kanade.tachiyomi.extension.tr.eldermanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class ElderManga : UzayManga() {
    override val cdnUrl = "https://cdn-el.efsaneler2.can.re"
}
