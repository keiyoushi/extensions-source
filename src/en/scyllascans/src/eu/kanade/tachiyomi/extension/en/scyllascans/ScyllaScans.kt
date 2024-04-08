package eu.kanade.tachiyomi.extension.en.scyllascans

import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class ScyllaScans : FuzzyDoodle("Scylla Scans", "https://scyllascans.org", "en") {

    // readerfront -> fuzzydoodle
    override val versionId = 2

    override val latestFromHomePage = true
}
