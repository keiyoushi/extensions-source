package eu.kanade.tachiyomi.extension.ja.rawsakura

import eu.kanade.tachiyomi.multisrc.rawspa.RawSpa
import keiyoushi.annotation.Source

@Source
abstract class RawSakura : RawSpa() {
    override val mangaPath = "title"

    override val readerPath = "reader"

    override val idPrefix = "sa"
}
