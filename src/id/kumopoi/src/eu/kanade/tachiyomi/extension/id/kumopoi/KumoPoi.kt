package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class KumoPoi : MangaThemesia() {
    override val hasProjectPage = true
}
