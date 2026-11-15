package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class MerlinScans : InitManga() {

    override val popularUrlSlug = "seri-siralamasi"

    override val latestUrlSlug = "son-guncellenenler"
}
