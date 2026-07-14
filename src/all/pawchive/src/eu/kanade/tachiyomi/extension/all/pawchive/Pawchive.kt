package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.multisrc.pawchive.Pawchive
import keiyoushi.annotation.Source

@Source
abstract class Pawchive : Pawchive() {
    override val getTypes = listOf(
        "Patreon",
        "Pixiv Fanbox",
    )
}