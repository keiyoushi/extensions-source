package eu.kanade.tachiyomi.extension.tr.orimanga

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class OriManga : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-siralamasi"

    override val latestUrlSlug = "yakin-zamanda-guncellendi"
}
