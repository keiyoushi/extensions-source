package eu.kanade.tachiyomi.extension.tr.siyahmelek

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class Siyahmelek : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val latestUrlSlug = "recently-updated"

    override val popularUrlSlug = "manga-ranking"
}
