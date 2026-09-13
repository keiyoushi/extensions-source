package eu.kanade.tachiyomi.extension.en.meitoon

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import keiyoushi.annotation.Source

@Source
abstract class MeiToon : Keyoapp() {
    override fun popularMangaSelector(): String = ".series-splide .splide__slide:not(.splide__slide--clone)"
}
