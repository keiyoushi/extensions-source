package eu.kanade.tachiyomi.extension.all.mangamulti

import eu.kanade.tachiyomi.multisrc.mangaverse.Mangaverse
import keiyoushi.annotation.Source

@Source
abstract class MangaMulti : Mangaverse() {
    override val siteLang get() = lang.substringBefore("-")
}
