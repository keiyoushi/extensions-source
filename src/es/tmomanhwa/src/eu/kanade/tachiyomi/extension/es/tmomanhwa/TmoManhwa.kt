package eu.kanade.tachiyomi.extension.es.tmomanhwa

import eu.kanade.tachiyomi.multisrc.yiimanga.GenreFilter
import eu.kanade.tachiyomi.multisrc.yiimanga.Yiimanga
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl

@Source
abstract class TmoManhwa : Yiimanga() {
    override val mangaPath = "manhwa"

    override fun genreUrl(genre: String, page: Int): HttpUrl = listUrl("genero", genre, page = page).build()

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Se ignora al buscar por texto"),
        GenreFilter("Género", spanishGenres),
    )
}
