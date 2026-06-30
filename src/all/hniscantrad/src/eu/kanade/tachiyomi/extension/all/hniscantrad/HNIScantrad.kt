package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json

@Source
abstract class HNIScantrad : PizzaReader() {
    override val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun String.toStatus(): Int = if (isEmpty()) {
        SManga.UNKNOWN
    } else {
        when (substring(0, 7)) {
            "In cors" -> SManga.ONGOING
            "On goin" -> SManga.ONGOING
            "Complet" -> SManga.COMPLETED
            "Conclus" -> SManga.COMPLETED
            "Conclud" -> SManga.COMPLETED
            "Licenzi" -> SManga.LICENSED
            "License" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
    }
}
