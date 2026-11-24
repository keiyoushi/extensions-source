package eu.kanade.tachiyomi.extension.en.kunmangacouk

import eu.kanade.tachiyomi.multisrc.zinmangaart.ZinMangaArt
import keiyoushi.annotation.Source

@Source
abstract class KunMangaCoUk : ZinMangaArt() {
    override val searchPath = "/home"
}
