package eu.kanade.tachiyomi.extension.ja.rawuwu

import eu.kanade.tachiyomi.multisrc.rawspa.RawSpa
import keiyoushi.annotation.Source

@Source
abstract class RawUwU : RawSpa() {
    override val mangaPath = "raw"

    override val readerPath = "read"
}
