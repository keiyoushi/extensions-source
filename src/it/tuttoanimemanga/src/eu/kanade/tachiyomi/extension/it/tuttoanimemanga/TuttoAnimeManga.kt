package eu.kanade.tachiyomi.extension.it.tuttoanimemanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import kotlinx.serialization.json.Json

class TuttoAnimeManga : PizzaReader("TuttoAnimeManga", "https://tuttoanimemanga.net", "it") {
    override val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
