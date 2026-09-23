package eu.kanade.tachiyomi.extension.tr.korelimanga

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class KoreliManga : InitManga() {

    override val mangaUrlDirectory = "manga"
    override val popularUrlSlug = "manga-ranking"
    override val latestUrlSlug = "recently-updated"
    override val chapterPagePathSegment = "chapter"
}
