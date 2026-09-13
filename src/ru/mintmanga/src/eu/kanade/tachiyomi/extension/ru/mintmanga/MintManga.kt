package eu.kanade.tachiyomi.extension.ru.mintmanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import keiyoushi.annotation.Source

@Source
abstract class MintManga : GroupLe() {
    override val siteId: Int = 2
}
