package eu.kanade.tachiyomi.extension.en.kunmangato

enum class KunMangaToFilter(val queryParam: String) {
    Genre("manga_genre_id"),
    Type("manga_type_id"),
    Status("status"),
}

typealias OptionName = String

typealias OptionValue = String

typealias OptionValueOptionNamePair = Pair<OptionValue, OptionName>
