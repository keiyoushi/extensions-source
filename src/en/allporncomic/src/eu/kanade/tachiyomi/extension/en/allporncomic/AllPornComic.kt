package eu.kanade.tachiyomi.extension.en.allporncomic

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class AllPornComic : MadaraNoAjax() {
    override val mangaSubString = "porncomic"
}
