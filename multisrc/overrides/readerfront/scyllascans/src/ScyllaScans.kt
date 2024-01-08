package eu.kanade.tachiyomi.extension.en.scyllascans

import eu.kanade.tachiyomi.multisrc.readerfront.ReaderFront

class ScyllaScans : ReaderFront("Scylla Scans", "https://scyllascans.org", "en") {
    override fun getImageCDN(path: String, width: Int) =
        "https://i${(0..2).random()}.wp.com/api.scyllascans.org" +
            "$path?strip=all&quality=100&w=$width"
}
