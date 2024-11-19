package eu.kanade.tachiyomi.extension.en.resetscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ResetScans : Madara(
    "Reset Scans",
    "https://rspro.xyz",
    "en",
) {
    // Moved from FuzzyDoodle to Madara
    override val versionId = 3

    override fun chapterListSelector() = "li.wp-manga-chapter>div:not(:has(a[href*=#]))"
}
