package eu.kanade.tachiyomi.extension.en.manhwaddict

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class Manhwaddict : Yiimanga() {
    override val mangaPath = "manga"

    override val searchParam = "q"
}
