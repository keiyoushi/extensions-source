package eu.kanade.tachiyomi.extension.es.begatranslation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.get
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class BegaTranslation : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale.forLanguageTag("es"))
    override val chapterMode = ChapterMode.MangaAjax
    override val mangaSubString = "series"

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replaceFirst(if (fromSearch) "-193x278" else "-175x238", "")
}
