package eu.kanade.tachiyomi.extension.en.manhwatoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import okhttp3.Headers

@Source
abstract class ManhwaToon : Madara() {
    override val chapterMode = ChapterMode.MangaAjax

    override fun Headers.Builder.configureHeaders() = apply {
        removeAll("Origin")
    }
}
