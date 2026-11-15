package eu.kanade.tachiyomi.extension.en.mangatoday

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaToday : MangaHub() {
    override val mangaSource = "m03"
}
