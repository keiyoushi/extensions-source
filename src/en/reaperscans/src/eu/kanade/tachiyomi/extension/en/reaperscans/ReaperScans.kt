package eu.kanade.tachiyomi.extension.en.reaperscans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class ReaperScans : HeanCms("Reaper Scans", "https://reaperscans.com", "en") {

    override val versionId = 2

    override val useNewChapterEndpoint = true
    override val useNewQueryEndpoint = true
    override val enableLogin = true

    override val id = 5177220001642863679

    override val cdnUrl = "https://media.reaperscans.com/file/4SRBHm"
}
