package eu.kanade.tachiyomi.extension.en.manhwahubto

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhwaHubTo : Madara() {
    override val mangaSubString = "manhwa"
    override val genreDirectory = "genre"
}
