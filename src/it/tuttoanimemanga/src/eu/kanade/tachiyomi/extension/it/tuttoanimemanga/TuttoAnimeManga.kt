package eu.kanade.tachiyomi.extension.it.tuttoanimemanga

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import kotlinx.serialization.json.Json

class TuttoAnimeManga : PizzaReader("TuttoAnimeManga", "http://tuttoanimemanga.net", "it") {
    override val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
