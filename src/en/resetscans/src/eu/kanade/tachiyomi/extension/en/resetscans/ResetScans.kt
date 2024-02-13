package eu.kanade.tachiyomi.extension.en.resetscans
import eu.kanade.tachiyomi.multisrc.madara.Madara

class ResetScans : Madara("Reset Scans", "https://reset-scans.us", "en") {
    override val useNewChapterEndpoint = true
    override val chapterUrlSelector = ".li__text > a"
}
