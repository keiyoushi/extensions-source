package eu.kanade.tachiyomi.extension.th.kingsmanga

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class UpdatesFilter :
    SelectFilter(
        "อัพเดท",
        arrayOf(
            "การ์ตูนล่าสุด" to "",
            "การ์ตูน 18+" to "h-manga-update/",
        ),
    )
