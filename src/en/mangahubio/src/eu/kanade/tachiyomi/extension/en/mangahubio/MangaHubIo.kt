package eu.kanade.tachiyomi.extension.en.mangahubio

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaHubIo : MangaHub() {
    override val mangaSource = "m01"
}
