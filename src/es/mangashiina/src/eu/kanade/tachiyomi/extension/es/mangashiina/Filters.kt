package eu.kanade.tachiyomi.extension.es.mangashiina

import eu.kanade.tachiyomi.source.model.Filter
import java.text.Normalizer

class SortFilter(state: Int = VIEWS) : Filter.Select<String>("Ordenar por", arrayOf("Más vistos", "Actualizados", "Nuevos", "A-Z"), state) {
    fun sort(mangas: List<MangaDto>): List<MangaDto> = when (state) {
        VIEWS -> mangas.sortedByDescending { it.totalViews }
        UPDATED -> mangas.sortedByDescending { it.latestUpdateAt }
        NEWEST -> mangas.sortedByDescending { it.publishedAt }
        else -> mangas.sortedBy { it.titulo.normalized() }
    }

    companion object {
        const val VIEWS = 0
        const val UPDATED = 1
        private const val NEWEST = 2
    }
}

class TypeFilter(types: List<String>) : Filter.Select<String>("Tipo", (listOf("Todos") + types).toTypedArray()) {
    val selected get() = if (state == 0) null else values[state]
}

class Genre(name: String) : Filter.TriState(name)

class GenreFilter(genres: List<String>) : Filter.Group<Genre>("Géneros", genres.map(::Genre)) {
    fun matches(mangaGenres: List<String>): Boolean = state.all {
        when {
            it.isIncluded() -> it.name in mangaGenres
            it.isExcluded() -> it.name !in mangaGenres
            else -> true
        }
    }
}

private val DIACRITICS = Regex("""\p{Mn}+""")

fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
