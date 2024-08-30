package eu.kanade.tachiyomi.extension.en.resetscans
import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ResetScans : Madara(
    "Reset Scans",
    "https://reset-scans.co",
    "en",
    dateFormat = SimpleDateFormat("MMM dd", Locale("en")),
) {

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
    override fun chapterListSelector(): String = "li.wp-manga-chapter.free-chap"
}
