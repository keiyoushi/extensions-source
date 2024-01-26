package eu.kanade.tachiyomi.extension.pt.lermangaonline

import eu.kanade.tachiyomi.source.model.Filter

open class Genre(val name: String, val slug: String) {
    fun isGlobal() = Global.slug == slug
    override fun toString() = name

    companion object {
        val Global = Genre("Global", "global")
    }
}

open class GenreFilter<String>(name: String, values: Array<Genre>) : Filter.Select<Genre>(name.toString(), values) {
    val selected
        get() = values[state]
}

object LerMangaOnlineFilters {
    val GenresFilter = GenreFilter(
        "Categorias",
        arrayOf(
            Genre.Global,
            Genre("Ação", "acao"),
            Genre("Action", "action"),
            Genre("Adulto", "adulto"),
            Genre("Adulto (18+)", "adulto-18"),
            Genre("Adulto (YAOI)", "adulto-yaoi"),
            Genre("Artes Marciais", "artes-marciais"),
            Genre("Ativo", "ativo"),
            Genre("Aventura", "aventura"),
            Genre("Comédia", "comedia"),
            Genre("Comedy", "comedy"),
            Genre("Demência", "demencia"),
            Genre("Demônios", "demonios"),
            Genre("Doujinshi", "doujinshi"),
            Genre("Drama", "drama"),
            Genre("Ecchi", "ecchi"),
            Genre("Escolar", "escolar"),
            Genre("Espacial", "espacial"),
            Genre("Esportes", "esportes"),
            Genre("Fantasia", "fantasia"),
            Genre("Fantasy", "fantasy"),
            Genre("Ficção", "ficcão"),
            Genre("Ficção Científica", "ficcão-científica"),
            Genre("FullColor", "fullcolor"),
            Genre("Gender Bender", "gender-bender"),
            Genre("Harém", "harem"),
            Genre("Hentai", "hentai"),
            Genre("Histórico", "historico"),
            Genre("Horror", "horror"),
            Genre("Isekai", "isekai"),
            Genre("Jogos", "jogos"),
            Genre("Josei", "josei"),
            Genre("LongStrip", "longstrip"),
            Genre("Maduro", "maduro"),
            Genre("Mafia", "mafia"),
            Genre("Magia", "magia"),
            Genre("Mangás", "mangas"),
            Genre("Manhwa", "manhwa"),
            Genre("MartialArts", "martialarts"),
            Genre("Mechas", "mechas"),
            Genre("Militar", "militar"),
            Genre("Mistério", "mistério"),
            Genre("Monstros", "monstros"),
            Genre("Música", "música"),
            Genre("One Shot", "One Shot"),
            Genre("Paródia", "parodia"),
            Genre("Psicológico", "psicologico"),
            Genre("Romance", "romance"),
            Genre("SchoolLife", "schoollife"),
            Genre("Sci-Fi", "sci-fi"),
            Genre("Seinen", "seinen"),
            Genre("Shonen", "shonen"),
            Genre("Shoujo", "shoujo"),
            Genre("Shoujo Ai", "shoujo-ai"),
            Genre("Shounen", "shounen"),
            Genre("Shounen Ai", "shounen-ai"),
            Genre("Slice of Life", "slice-of-life"),
            Genre("Sobrenatural", "sobrenatural"),
            Genre("Sports", "sports"),
            Genre("Super Poderes", "super-poderes"),
            Genre("Thriller", "thriller"),
            Genre("TimeTravel", "timeTravel"),
            Genre("Tragédia", "tragedia"),
            Genre("UserCreated", "usercreated"),
            Genre("Vampiros", "vampiros"),
            Genre("Vida Escolar", "vida-escolar"),
            Genre("VideoGames", "videogames"),
            Genre("WebComic", "webComic"),
            Genre("Webtoon", "webtoon"),
            Genre("Yaoi", "yaoi"),
            Genre("Yuri", "yuri"),
        ),
    )
}
