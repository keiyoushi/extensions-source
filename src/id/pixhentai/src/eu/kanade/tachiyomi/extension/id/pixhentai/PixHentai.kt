package eu.kanade.tachiyomi.extension.id.pixhentai

import eu.kanade.tachiyomi.multisrc.oceanwp.OceanWP
import keiyoushi.annotation.Source

@Source
abstract class PixHentai : OceanWP() {
    override val hasTagFilter = false
}
