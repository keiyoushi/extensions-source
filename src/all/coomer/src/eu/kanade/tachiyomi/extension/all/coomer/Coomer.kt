package eu.kanade.tachiyomi.extension.all.coomer

import eu.kanade.tachiyomi.multisrc.kemono.Kemono
import keiyoushi.annotation.Source

@Source
abstract class Coomer : Kemono() {
    override val getTypes = listOf(
        "OnlyFans",
        "Fansly",
        "CandFans",
    )
}
