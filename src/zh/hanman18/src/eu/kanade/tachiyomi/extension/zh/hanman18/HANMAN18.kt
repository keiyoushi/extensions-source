package eu.kanade.tachiyomi.extension.zh.hanman18

import eu.kanade.tachiyomi.multisrc.manga18.Manga18
import keiyoushi.annotation.Source

@Source
abstract class HANMAN18 : Manga18() {
    // tag filter doesn't work on site
    override val getAvailableTags = false
}
