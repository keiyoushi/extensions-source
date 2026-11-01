package eu.kanade.tachiyomi.extension.es.leermanhwas

import eu.kanade.tachiyomi.multisrc.yiimanga.GenreFilter
import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl

@Source
abstract class Leermanhwas : Yiimanga() {
    override val mangaPath = "manhwa"

    override fun genreUrl(genre: String, page: Int): HttpUrl = listUrl("genero", genre, page = page).build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Se ignora al buscar por texto"),
        GenreFilter("Género", spanishGenres),
    )
}
