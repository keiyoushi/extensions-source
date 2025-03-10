package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import java.text.SimpleDateFormat
import java.util.Locale

class HolyManga : FMReader(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
) {
    override val versionId = 2

    override val chapterUrlSelector = ""
}
