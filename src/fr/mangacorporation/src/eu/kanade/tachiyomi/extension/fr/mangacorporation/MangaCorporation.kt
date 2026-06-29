package eu.kanade.tachiyomi.extension.fr.mangacorporation

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import kotlinx.serialization.json.Json

class MangaCorporation : PizzaReader("Manga-Corporation", "https://manga-corporation.com", "fr") {
    override val json: Json = Json {
        coerceInputValues = true
        ignoreUnknownKeys = true
    }
}
