package eu.kanade.tachiyomi.extension.tr.ragnarscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class RagnarScans : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "en-cok-takip-edilenler"
}
