package eu.kanade.tachiyomi.extension.all.kemono

import eu.kanade.tachiyomi.multisrc.kemono.Kemono

class Kemono : Kemono("Kemono", "https://kemono.su", "all") {
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
