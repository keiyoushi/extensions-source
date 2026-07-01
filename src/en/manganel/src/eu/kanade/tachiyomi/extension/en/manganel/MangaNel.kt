package eu.kanade.tachiyomi.extension.en.manganel

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaNel : MangaHub() {
    override val mangaSource = "mn05"
}
