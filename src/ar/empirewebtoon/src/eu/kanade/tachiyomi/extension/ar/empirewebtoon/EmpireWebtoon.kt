package eu.kanade.tachiyomi.extension.ar.empirewebtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EmpireWebtoon : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar"))
    override val mangaSubString = "webtoon"
    override val useNewChapterEndpoint = false
}
