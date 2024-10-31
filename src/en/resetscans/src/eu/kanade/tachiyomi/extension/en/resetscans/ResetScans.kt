package eu.kanade.tachiyomi.extension.en.resetscans
import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class ResetScans : FuzzyDoodle(
    "Reset Scans",
    "https://reset-scans.xyz",
    "en",
) {
    override val latestFromHomePage = true

    // Moved from Madara to FuzzyDoodle
    override val versionId = 2
}
