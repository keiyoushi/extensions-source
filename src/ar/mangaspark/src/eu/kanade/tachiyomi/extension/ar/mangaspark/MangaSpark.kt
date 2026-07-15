package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaSpark : Madara() {

    override val dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar"))
    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
    override val pageListParseSelector = "div.wp-manga-chapter-img img, div.reading-content img"
}
