package eu.kanade.tachiyomi.extension.ru.unicomics

import eu.kanade.tachiyomi.source.model.Filter

class Publishers(publishers: Map<String, String>) : Filter.Select<String>("Издательства (только)", publishers.keys.toTypedArray()) {
    val urls = publishers.values.toTypedArray()
}

class GetEventsList : Filter.Select<String>("События (только)", arrayOf("Нет", "в комиксах"))

internal data class Publisher(val name: String, val url: String)
