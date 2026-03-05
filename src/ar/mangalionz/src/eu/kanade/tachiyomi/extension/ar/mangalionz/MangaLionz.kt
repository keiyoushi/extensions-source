package eu.kanade.tachiyomi.extension.ar.mangalionz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLionz :
    Madara(
        "MangaLionz",
        "https://manga-lionz.org",
        "ar",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val chapterUrlSuffix = ""
}
