package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaStarz : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar"))
    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
