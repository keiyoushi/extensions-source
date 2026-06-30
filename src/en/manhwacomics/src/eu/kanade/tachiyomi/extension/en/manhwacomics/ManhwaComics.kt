package eu.kanade.tachiyomi.extension.en.manhwacomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ManhwaComics : Madara() {
    override val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
    override val mangaSubString = "manhwa"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
