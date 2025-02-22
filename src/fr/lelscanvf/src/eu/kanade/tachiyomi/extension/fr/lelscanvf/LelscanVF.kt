package eu.kanade.tachiyomi.extension.fr.lelscanvf
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle

class LelscanVF : FuzzyDoodle("Lelscan-VF", "https://lelscanfr.com", "fr") {

    // mmrcms -> FuzzyDoodle
    override val versionId = 2

    override val latestFromHomePage = true
}
