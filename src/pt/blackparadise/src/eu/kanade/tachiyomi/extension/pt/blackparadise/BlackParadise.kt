package eu.kanade.tachiyomi.extension.pt.blackparadise

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class BlackParadise : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val altNameSelector = ".post-content_item:contains(Nome) .summary-content"
    override val hiatusStatus = super.hiatusStatus + "hiato"
    override val cancelledStatus = super.cancelledStatus + "dropado"

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    companion object {
        // WordPress resizes ("-175x238") of the original cover
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
