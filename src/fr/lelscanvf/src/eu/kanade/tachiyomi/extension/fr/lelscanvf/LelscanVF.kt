package eu.kanade.tachiyomi.extension.fr.lelscanvf

import eu.kanade.tachiyomi.multisrc.fuzzydoodle.FuzzyDoodle
import keiyoushi.annotation.Source

@Source
abstract class LelscanVF : FuzzyDoodle() {

    override val latestFromHomePage = true
}
