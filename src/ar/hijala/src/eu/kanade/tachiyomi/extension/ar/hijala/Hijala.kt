package eu.kanade.tachiyomi.extension.ar.hijala

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.Request
import okhttp3.Response

class Hijala : ZeistManga("Hijala", "https://hijala.blogspot.com", "ar") {

    override val hasFilters = true
    override val hasLanguageFilter = false

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun getGenreList(): List<Genre> = listOf(
        Genre("أكشن", "Action"),
        Genre("أثارة", "Thriller"),
        Genre("أتشي", "Ecchi"),
        Genre("حياة مدرسية", "School Life"),
        Genre("تاريخي", "Historical"),
        Genre("ألعاب", "Game"),
        Genre("خيال علمي", "Sci-Fi"),
        Genre("خيال", "Fantasy"),
        Genre("خارق للطبيعة", "Supernatural"),
        Genre("رومانسي", "Romance"),
        Genre("رعب", "Horror"),
        Genre("دراما", "Drama"),
        Genre("سينين", "Seinen"),
        Genre("سحري", "Magic"),
        Genre("رياضي", "Sports"),
        Genre("شونين", "Shounen"),
        Genre("شوجو", "Shoujo"),
        Genre("شريحة من الحياة", "Slice of Life"),
        Genre("علاجي", "Medical"),
        Genre("عسكري", "Military"),
        Genre("طبخ", "Cooking"),
        Genre("فنون قتال", "Martial Arts"),
        Genre("غموض", "Mystery"),
        Genre("عوالم متعددة", "Isekai"),
        Genre("مانها", "مانها"),
        Genre("مأساوي", "Tragedy"),
        Genre("كوميديا", "Comedy"),
        Genre("مغامرات", "Adventure"),
        Genre("مصاص دماء", "مصاص دماء"),
        Genre("مانهوا", "مانهوا"),
        Genre("موسيقي", "موسيقي"),
        Genre("موسيقى", "Music"),
        Genre("مغامرات", "مغامرات"),
        Genre("نفسي", "نفسي"),
        Genre("نفسي", "Psychological"),
        Genre("ميكا", "ميكا"),
    )
}
