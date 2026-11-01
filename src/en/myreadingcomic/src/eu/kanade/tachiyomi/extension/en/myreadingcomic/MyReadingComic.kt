package eu.kanade.tachiyomi.extension.en.myreadingcomic

import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import keiyoushi.annotation.Source

@Source
abstract class MyReadingComic : Yiimanga() {
    override val mangaPath = "comic"

    override val searchParam = "q"

    override val popularPath = "porn-comics"
}
