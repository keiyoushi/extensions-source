package eu.kanade.tachiyomi.extension.en.onemangainfo

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

// Some chapters link to 1manga.co, hard to filter
@Source
abstract class OneMangaInfo : MangaHub() {
    override val mangaSource = "mh01"
}
