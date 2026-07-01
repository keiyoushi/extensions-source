package eu.kanade.tachiyomi.extension.en.beehentai

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import keiyoushi.annotation.Source

@Source
abstract class BeeHentai : MadTheme() {
    override val useSlugSearch = true
}
