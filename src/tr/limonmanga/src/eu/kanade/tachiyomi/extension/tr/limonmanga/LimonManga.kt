package eu.kanade.tachiyomi.extension.tr.limonmanga

import eu.kanade.tachiyomi.multisrc.uzaymanga.UzayManga
import keiyoushi.annotation.Source

@Source
abstract class LimonManga : UzayManga() {
    override val cdnUrl = "https://cdn-l.efsaneler2.can.re"
}
