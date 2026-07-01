package eu.kanade.tachiyomi.extension.en.mangahereonl

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaHereOnl : MangaHub() {
    override val mangaSource = "mh01"
}
