package eu.kanade.tachiyomi.extension.ru.seimanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import keiyoushi.annotation.Source

@Source
abstract class SeiManga : GroupLe() {
    override val siteId: Int = 21
}
