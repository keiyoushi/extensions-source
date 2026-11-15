package eu.kanade.tachiyomi.extension.en.mangapandaonl

import eu.kanade.tachiyomi.multisrc.mangahub.MangaHub
import keiyoushi.annotation.Source

@Source
abstract class MangaPandaOnl : MangaHub() {
    override val mangaSource = "mr02"
}
