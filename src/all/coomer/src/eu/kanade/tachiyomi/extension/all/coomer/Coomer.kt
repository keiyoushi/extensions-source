package eu.kanade.tachiyomi.extension.all.coomer

import eu.kanade.tachiyomi.multisrc.kemono.Kemono

class Coomer : Coomer("Coomer", "https://coomer.su", "all") {
    override val getTypes = listOf(
        "OnlyFans",
        "Fansly",
        "CandFans"
    )
}
