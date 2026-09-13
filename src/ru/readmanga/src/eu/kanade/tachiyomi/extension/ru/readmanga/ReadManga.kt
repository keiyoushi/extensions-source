package eu.kanade.tachiyomi.extension.ru.readmanga

import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import keiyoushi.annotation.Source

@Source
abstract class ReadManga : GroupLe() {
    override val siteId: Int = 1
}
