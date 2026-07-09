package eu.kanade.tachiyomi.extension.en.toonilyme

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import keiyoushi.annotation.Source

@Source
abstract class ToonilyMe : MadTheme() {
    override val useSlugSearch = true
}
