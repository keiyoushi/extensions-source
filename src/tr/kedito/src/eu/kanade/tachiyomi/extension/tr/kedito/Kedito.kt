package eu.kanade.tachiyomi.extension.tr.kedito

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Kedito : Madara(
    "Kedi.to",
    "https://kedi.to",
    "tr",
    SimpleDateFormat("dd MMM yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val chapterUrlSuffix = ""
}
