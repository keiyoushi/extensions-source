package eu.kanade.tachiyomi.extension.es.menudofansub

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import keiyoushi.annotation.Source

@Source
abstract class MenudoFansub : FoolSlide() {

    override val urlModifier = "/slide"
}
