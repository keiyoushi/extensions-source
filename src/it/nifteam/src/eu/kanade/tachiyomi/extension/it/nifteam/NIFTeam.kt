package eu.kanade.tachiyomi.extension.it.nifteam

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import keiyoushi.annotation.Source

@Source
abstract class NIFTeam : FoolSlide() {

    override val urlModifier = "/slide"
}
