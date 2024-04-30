package eu.kanade.tachiyomi.extension.en.fleksyscans

import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class FleksyScans : FuzzyDoodle("FleksyScans", "https://flexscans.com", "en") {
    override fun chapterListSelector() = "div#chapters-list > a[href]:not(:has(span:contains(vip)))"
}
