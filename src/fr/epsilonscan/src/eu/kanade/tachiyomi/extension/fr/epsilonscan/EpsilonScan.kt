package eu.kanade.tachiyomi.extension.fr.epsilonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class EpsilonScan : Madara(
    "Epsilon Scan",
    "https://epsilonscan.to",
    "fr",
    SimpleDateFormat("dd/MM/yy", Locale.FRENCH),
) {
    // Site moved from MangaThemesia to Madara
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val useNewChapterEndpoint = true
}
