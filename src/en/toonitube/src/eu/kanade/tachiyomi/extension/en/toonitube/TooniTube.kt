package eu.kanade.tachiyomi.extension.en.toonitube

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import keiyoushi.annotation.Source

@Source
abstract class TooniTube : MadTheme() {
    override val useSlugSearch = true
}
