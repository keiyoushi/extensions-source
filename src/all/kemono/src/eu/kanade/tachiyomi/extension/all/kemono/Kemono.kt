package eu.kanade.tachiyomi.extension.all.kemono

import eu.kanade.tachiyomi.multisrc.kemono.Kemono
import keiyoushi.annotation.Source

@Source
abstract class Kemono : Kemono() {
    override val getTypes = listOf(
        "Patreon",
        "Pixiv Fanbox",
        "Discord",
        "Fantia",
        "Afdian",
        "Boosty",
        "Gumroad",
        "SubscribeStar",
    )
}
