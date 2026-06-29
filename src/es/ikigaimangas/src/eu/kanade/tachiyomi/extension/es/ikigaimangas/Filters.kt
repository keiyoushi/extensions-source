package eu.kanade.tachiyomi.extension.es.ikigaimangas

import eu.kanade.tachiyomi.source.model.Filter

class Genre(title: String, val id: Long) : Filter.CheckBox(title)
class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

class Status(title: String, val id: Long) : Filter.CheckBox(title)
class StatusFilter(title: String, statuses: List<Status>) : Filter.Group<Status>(title, statuses)

class SortByFilter(title: String, private val sortProperties: List<SortProperty>) :
    Filter.Sort(
        title,
        sortProperties.map { it.name }.toTypedArray(),
        Selection(2, ascending = false),
    ) {
    val selected: String
        get() = sortProperties[state!!.index].value
}

class SortProperty(val name: String, val value: String) {
    override fun toString(): String = name
}

fun getSortProperties(): List<SortProperty> = listOf(
    SortProperty("Nombre", "name"),
    SortProperty("Creado en", "created_at"),
    SortProperty("Actualización más reciente", "last_chapter_date"),
    SortProperty("Número de favoritos", "bookmark_count"),
    SortProperty("Número de valoración", "rating_count"),
    SortProperty("Número de vistas", "view_count"),
)

fun getStatusFilters(): List<Status> = listOf(
    Status("Abandonada", 906428048651190273L),
    Status("Cancelada", 906426661911756802L),
    Status("Completa", 906409532796731395L),
    Status("En Curso", 911437469204086787L),
    Status("Hiatus", 906409397258190851L),
)

fun getGenreFilters(): List<Genre> = listOf(
    Genre("+18", 906409351272792067L),
    Genre("Acción", 906397904327999491L),
    Genre("Adulto", 906409527934582787L),
    Genre("Apocalíptico", 906409378635186179L),
    Genre("Artes Marciales", 906397904169861123L),
    Genre("Aventura", 906397904061530115L),
    Genre("Boys Love", 906409351330037763L),
    Genre("Ciencia Ficción", 906409468787720195L),
    Genre("Comedia", 906398112851165187L),
    Genre("Demonios", 906397904115531779L),
    Genre("Deportes", 906410143226462211L),
    Genre("Doujinshi", 1187154685166452739L),
    Genre("Drama", 906397903933407235L),
    Genre("Ecchi", 906409370648543235L),
    Genre("Familia", 906409382485884931L),
    Genre("Fantasía", 906397894348570627L),
    Genre("Gender Bender", 1093357252096753667L),
    Genre("Girls Love", 906409644012961795L),
    Genre("Gore", 906409472386203651L),
    Genre("Guideverse", 1182242384692314113L),
    Genre("Harem", 906397904221962243L),
    Genre("Harem Inverso", 906424352006438914L),
    Genre("Histórico", 906398112923385859L),
    Genre("Horror", 906423434084679682L),
    Genre("Isekai", 906409454067646467L),
    Genre("Josei", 906409501957390339L),
    Genre("Maduro", 906409612041551875L),
    Genre("Magia", 906409459593347075L),
    Genre("Manga", 1187155307072782337L),
    Genre("Mecha", 906409472453410819L),
    Genre("Militar", 906409472509739011L),
    Genre("Misterio", 906409374254727171L),
    Genre("Omegaverse", 1182242409543827457L),
    Genre("Psicológico", 906409351382073347L),
    Genre("Realidad Virtual", 906424676182294530L),
    Genre("Recuentos de la vida", 906409508165124099L),
    Genre("Reencarnación", 906409400553046019L),
    Genre("Regresion", 906397894469255171L),
    Genre("Romance", 906397894527549443L),
    Genre("Seinen", 906397903999959043L),
    Genre("Shonen", 906398112991150083L),
    Genre("Shoujo", 906397894408372227L),
    Genre("Shoujo Ai", 1187155022664531971L),
    Genre("Shounen Ai", 1187155082787848194L),
    Genre("Sistema", 906409408107216899L),
    Genre("Smut", 906409419999641603L),
    Genre("Supernatural", 906410027513937923L),
    Genre("Supervivencia", 906409454130921475L),
    Genre("Tragedia", 906409449984655363L),
    Genre("Transmigración", 906409378688663555L),
    Genre("Vida Escolar", 906409508232822787L),
    Genre("Yaoi", 906409432216403971L),
    Genre("Yuri", 906409472567017475L),
)
