package eu.kanade.tachiyomi.extension.id.aarlas

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class Aarlas : ZeistManga() {
    override val preferChapterUpdatedDate = true
}
