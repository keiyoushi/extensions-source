package eu.kanade.tachiyomi.extension.id.sasangeyou

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class Sasangeyou : MangaThemesia() {
    override val datePattern = "MM/dd/yyyy"
    override val hasProjectPage = false
}
