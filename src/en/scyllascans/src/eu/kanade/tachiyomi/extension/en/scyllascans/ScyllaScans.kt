package eu.kanade.tachiyomi.extension.en.scyllascans

import eu.kanade.tachiyomi.multisrc.cloudrecess.CloudRecess

class ScyllaScans : CloudRecess("Scylla Scans", "https://scyllascans.org", "en") {

    // readerfront -> cloudrecess
    override val versionId = 2

    override val latestFromHomePage = true
}
