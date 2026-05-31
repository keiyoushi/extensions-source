package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ResetScans :
    Madara(
        "Reset Scans",
        "https://reset-scans.org",
        "en",
        dateFormat = SimpleDateFormat("dd-MMM", Locale.US),
    ) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(a[href*='#']))"
}
