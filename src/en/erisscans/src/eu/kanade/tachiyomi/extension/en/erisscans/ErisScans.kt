package eu.kanade.tachiyomi.extension.en.erisscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import keiyoushi.annotation.Source

@Source
abstract class ErisScans : Keyoapp() {
    override val altNameSelector: String = "div.font-medium:containsOwn(Alternative titles) ~ div span.select-all"
    override val statusSelector: String = "div[alt=Status]"
    override val authorSelector: String = "div[alt=Author]"
    override val artistSelector: String = "div[alt=Artist]"
    override val typeSelector: String = "div[alt='Series Type']"
}
