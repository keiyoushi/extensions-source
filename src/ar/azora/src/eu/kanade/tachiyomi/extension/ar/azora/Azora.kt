package eu.kanade.tachiyomi.extension.ar.azora

import eu.kanade.tachiyomi.multisrc.iken.Iken

class Azora :
    Iken(
        "Azora",
        "ar",
        "https://azorafly.com",
        "https://api.azorafly.com",
    ) {

    override val versionId = 2
}
