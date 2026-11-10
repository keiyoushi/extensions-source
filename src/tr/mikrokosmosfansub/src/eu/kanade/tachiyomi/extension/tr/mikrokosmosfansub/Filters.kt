package eu.kanade.tachiyomi.extension.tr.mikrokosmosfansub

import eu.kanade.tachiyomi.source.model.Filter

open class ValueFilter(name: String, private val options: Array<String>) : Filter.Select<String>(name, arrayOf("Tümü") + options) {
    val value get() = options.getOrNull(state - 1)
}

class TypeFilter : ValueFilter("Tür", arrayOf("Manhwa", "Webtoon", "Manga", "Manhua"))

class StatusFilter : ValueFilter("Durum", arrayOf("Devam Ediyor", "Tamamlandı"))
