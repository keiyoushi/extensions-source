package eu.kanade.tachiyomi.extension.it.tuttoanimemanga

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json

@Source
abstract class TuttoAnimeManga : PizzaReader() {
    override val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
