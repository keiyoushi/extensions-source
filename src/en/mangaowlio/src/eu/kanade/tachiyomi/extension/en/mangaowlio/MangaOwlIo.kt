package eu.kanade.tachiyomi.extension.en.mangaowlio

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class MangaOwlIo : Madara() {
    override val mangaSubString = "read-1"

    override val useNewChapterEndpoint = true
}
