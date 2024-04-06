package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.multisrc.pizzareader.PizzaReader
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json

class HNIScantrad : PizzaReader("HNI-Scantrad", "https://hni-scantrad.net", "all") {
    override val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun String.toStatus(): Int {
        return if (isEmpty()) {
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
}
