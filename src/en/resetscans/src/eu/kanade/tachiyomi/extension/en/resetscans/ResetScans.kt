package eu.kanade.tachiyomi.extension.en.resetscans
import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class ResetScans : FuzzyDoodle(
    "Reset Scans",
    "https://reset-scans.xyz",
    "en",
) {
    override val supportsLatest = false

    // Moved from Madara to FuzzyDoodle
    override val versionId = 2
}
