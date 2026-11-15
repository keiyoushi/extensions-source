package eu.kanade.tachiyomi.extension.en.mangareadersite

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaReaderSite : MangaHub() {
    override val mangaSource = "mr01"
}
