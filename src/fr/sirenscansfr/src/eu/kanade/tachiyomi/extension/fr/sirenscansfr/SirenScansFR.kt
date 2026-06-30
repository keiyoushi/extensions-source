package eu.kanade.tachiyomi.extension.fr.sirenscansfr

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import keiyoushi.annotation.Source

@Source
abstract class SirenScansFR : Keyoapp() {
    override fun popularMangaSelector(): String = "section.splide.series-splide a.splide__slide"
}
