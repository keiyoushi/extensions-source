package eu.kanade.tachiyomi.multisrc.readerfront

sealed class ReaderFrontI18N(val id: Int) {
    protected abstract val genres: Map<String, String>

    operator fun get(name: NameWrapper) =
        name.toString().let { genres[it] ?: it }

    object SPANISH : ReaderFrontI18N(1) {
        override val genres = mapOf(
            "action" to "Acción",
            "adult" to "Adulto",
            "adventure" to "Aventura",
            "comedy" to "Comedia",
            "doujinshi" to "Doujinshi",
            "drama" to "Drama",
            "ecchi" to "Ecchi",
            "fantasy" to "Fantasía",
            "gender_bender" to "Cambio de Sexo",
            "harem" to "Harem",
            "hentai" to "Hentai",
            "historical" to "Histórico",
            "horror" to "Horror",
            "martial_arts" to "Artes Marciales",
            "mature" to "Maduro",
            "mecha" to "Mecha",
            "mystery" to "Misterio",
            "psychological" to "Psicologico",
            "romance" to "Romance",
            "school_life" to "Vida Escolar",
            "sci_fi" to "Ciencia Ficción",
            "slice_of_life" to "Recuentos de la Vida",
            "smut" to "Smut",
            "sports" to "Deportes",
            "supernatural" to "Sobrenatural",
            "tragedy" to "Tragédia",
        )
    }

    object ENGLISH : ReaderFrontI18N(2) {
        override val genres = mapOf(
            "action" to "Action",
            "adult" to "Adult",
            "adventure" to "Adventure",
            "comedy" to "Comedy",
            "doujinshi" to "Doujinshi",
            "drama" to "Drama",
            "ecchi" to "Ecchi",
            "fantasy" to "Fantasy",
            "gender_bender" to "Gender Bender",
            "harem" to "Harem",
            "hentai" to "Hentai",
            "historical" to "Historical",
            "horror" to "Horror",
            "martial_arts" to "Martial Arts",
            "mature" to "Mature",
            "mecha" to "Mecha",
            "mystery" to "Mystery",
            "psychological" to "Psychological",
            "romance" to "Romance",
            "school_life" to "School Life",
            "sci_fi" to "Sci-fi",
            "slice_of_life" to "Slice Of Life",
            "smut" to "Smut",
            "sports" to "Sports",
            "supernatural" to "Supernatural",
            "tragedy" to "Tragedy",
        )
    }

    companion object {
        operator fun invoke(lang: String) = when (lang) {
            "es" -> SPANISH
            "en" -> ENGLISH
            else -> error("Unsupported language: $lang")
        }
    }
}
