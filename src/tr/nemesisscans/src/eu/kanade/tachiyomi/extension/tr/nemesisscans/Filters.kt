package eu.kanade.tachiyomi.extension.tr.nemesisscans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sırala",
        listOf(
            "En Yeni" to "newest",
            "En Eski" to "oldest",
            "A-Z" to "title",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Tür",
        listOf(
            "Tümü" to "",
            "Aksiyon" to "Action",
            "Macera" to "Adventure",
            "Komedi" to "Comedy",
            "Dram" to "Drama",
            "Fantastik" to "Fantasy",
            "Korku" to "Horror",
            "Gizem" to "Mystery",
            "Romantizm" to "Romance",
            "Bilim Kurgu" to "Sci-Fi",
            "Günlük Yaşam" to "Slice of Life",
            "Spor" to "Sports",
            "Doğaüstü" to "Supernatural",
            "Gerilim" to "Thriller",
            "Mecha" to "Mecha",
            "Müzik" to "Music",
            "Psikolojik" to "Psychological",
            "Ecchi" to "Ecchi",
            "Yetişkin" to "Hentai",
            "Yaoi" to "Yaoi",
            "Yuri" to "Yuri",
            "Shounen" to "Shounen",
            "Shoujo" to "Shoujo",
            "Seinen" to "Seinen",
            "Josei" to "Josei",
            "Isekai" to "Isekai",
            "Tarihi" to "Historical",
            "Okul" to "School",
            "Harem" to "Harem",
        ),
    )
