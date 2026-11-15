package eu.kanade.tachiyomi.extension.ru.mangalib

import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import keiyoushi.annotation.Source

@Source
abstract class MangaLib : LibGroup() {

    override val siteId: Int = 1 // Important in api calls
}
