package eu.kanade.tachiyomi.extension.ru.usagi

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import keiyoushi.annotation.Source

@Source
abstract class Usagi : GroupLe() {
    override val siteId: Int = 12
}
