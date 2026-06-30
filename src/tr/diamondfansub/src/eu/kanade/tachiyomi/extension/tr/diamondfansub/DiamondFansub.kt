package eu.kanade.tachiyomi.extension.tr.diamondfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DiamondFansub : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM", Locale("tr", "TR"))
    override val mangaSubString = "seri"
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorAuthor = ".manga-authors"
    override val mangaDetailsSelectorDescription = ".manga-info"
}
