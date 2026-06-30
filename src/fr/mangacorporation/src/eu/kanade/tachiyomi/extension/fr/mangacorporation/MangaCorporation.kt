package eu.kanade.tachiyomi.extension.fr.mangacorporation

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json

@Source
abstract class MangaCorporation : PizzaReader() {
    override val json: Json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }
}
