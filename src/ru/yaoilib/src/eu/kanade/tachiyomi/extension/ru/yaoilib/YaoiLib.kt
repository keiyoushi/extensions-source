package eu.kanade.tachiyomi.extension.ru.yaoilib

import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import keiyoushi.annotation.Source

@Source
abstract class YaoiLib : LibGroup() {

    override val siteId: Int = 2 // Important in api calls
}
