package eu.kanade.tachiyomi.extension.ru.selfmanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import keiyoushi.annotation.Source

@Source
abstract class SelfManga : GroupLe() {
    override val siteId: Int = 3
}
