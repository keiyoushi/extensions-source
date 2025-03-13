package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ResetScans : Madara(
    "Reset Scans",
    "https://reset-scans.co",
    "en",
    dateFormat = SimpleDateFormat("MMM dd", Locale.US),
) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter>div:not(:has(a[href*=#]))"
}
