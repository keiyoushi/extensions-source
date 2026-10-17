package eu.kanade.tachiyomi.extension.en.gourmetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class GourmetScans : Madara() {
    override val mangaSubString = "project"
    override val genreDirectory = "genre"
    override val ajaxTemplate = "wp-manga/content/content-archive"
}
