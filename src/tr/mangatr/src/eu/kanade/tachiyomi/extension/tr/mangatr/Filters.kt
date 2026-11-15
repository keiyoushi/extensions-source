package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>, defaultState: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultState) {
    fun toUriPart() = vals[state].second
}

class SortFilter : UriPartFilter("Sıralama", arrayOf("Ada Göre" to "name", "Popülerlik" to "views", "Son Güncelleme" to "last_update"), 2)
class SortDirectionFilter : UriPartFilter("Yön", arrayOf("Z → A" to "DESC", "A → Z" to "ASC"))
class StatusFilter : UriPartFilter("Yayın Durumu", arrayOf("Hepsi" to "", "Tamamlandı" to "1", "Devam Ediyor" to "2"))
class TranslationStatusFilter : UriPartFilter("Çeviri Durumu", arrayOf("Hepsi" to "", "Tamamlanan" to "1", "Devam Eden" to "4", "Bırakılan" to "2", "Olmayan" to "3"))
class AgeFilter : UriPartFilter("Yaş Sınırlaması", arrayOf("Hepsi" to "", "+16" to "16", "+18" to "18"))
class ContentTypeFilter : UriPartFilter("İçerik", arrayOf("Hepsi" to "", "Manga" to "1", "Anime" to "4", "Çizgi Roman" to "5", "Novel" to "2", "Webtoon" to "3"))
class GenreFilter : UriPartFilter("Tür", genrePairs)

private val genrePairs = arrayOf(
    "Hepsi" to "",
    "4_Koma" to "4_Koma",
    "Action" to "Action",
    "Adventure" to "Adventure",
    "Aliens" to "Aliens",
    "Art" to "Art",
    "Biography" to "Biography",
    "Bishoujo" to "Bishoujo",
    "Bishounen" to "Bishounen",
    "Comedy" to "Comedy",
    "Cooking" to "Cooking",
    "Crime" to "Crime",
    "Cyberpunk" to "Cyberpunk",
    "Dark Fantasy" to "Dark Fantasy",
    "Demons" to "Demons",
    "Doujinshi" to "Doujinshi",
    "Drama" to "Drama",
    "Ecchi" to "Ecchi",
    "Epic" to "Epic",
    "Fantasy" to "Fantasy",
    "Game" to "Game",
    "Gore" to "Gore",
    "Harem" to "Harem",
    "History" to "History",
    "Historical" to "Historical",
    "Horror" to "Horror",
    "Isekai" to "Isekai",
    "Josei" to "Josei",
    "Kodomo" to "Kodomo",
    "Magic" to "Magic",
    "Manhua" to "Manhua",
    "Manhwa" to "Manhwa",
    "Martial" to "Martial",
    "Mecha" to "Mecha",
    "Military" to "Military",
    "Miscellaneous" to "Miscellaneous",
    "Monster" to "Monster",
    "Music" to "Music",
    "Mystery" to "Mystery",
    "Novel" to "Novel",
    "Nudity" to "Nudity",
    "One_Shot" to "One_Shot",
    "Post-Apocalyptic" to "Post-Apocalyptic",
    "Psychological" to "Psychological",
    "Psychological Thriller" to "Psychological Thriller",
    "Romance" to "Romance",
    "School" to "School",
    "School Life" to "School Life",
    "Sci_fi" to "Sci_fi",
    "Seinen" to "Seinen",
    "Short" to "Short",
    "Shoujo" to "Shoujo",
    "Shoujo_Ai" to "Shoujo_Ai",
    "Shounen" to "Shounen",
    "Shounen_Ai" to "Shounen_Ai",
    "Slice of life" to "Slice of life",
    "Space" to "Space",
    "Sports" to "Sports",
    "Supernatural" to "Supernatural",
    "Superpower" to "Superpower",
    "Suspense" to "Suspense",
    "Thriller" to "Thriller",
    "Tragedy" to "Tragedy",
    "Türkçe Novel" to "Türkçe Novel",
    "Vampires" to "Vampires",
    "Villainess" to "Villainess",
    "Violence" to "Violence",
    "War" to "War",
    "Webtoon" to "Webtoon",
    "Western" to "Western",
    "Yuri" to "Yuri",
)
