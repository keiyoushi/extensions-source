package eu.kanade.tachiyomi.extension.fr.lelscanvf

import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class LelscanVF : FuzzyDoodle("Lelscan-VF", "https://lelscanfr.com", "fr") {

    // mmrcms -> FuzzyDoodle
    override val versionId = 2

    override val latestFromHomePage = true
}
