package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.multisrc.kemono.Kemono
import keiyoushi.annotation.Source

@Source
abstract class Pawchive : Kemono() {
    override val getTypes = listOf(
        "Patreon",
        "Pixiv Fanbox",
    )
}
