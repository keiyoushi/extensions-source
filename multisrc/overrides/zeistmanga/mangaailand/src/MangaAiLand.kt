package eu.kanade.tachiyomi.extension.ar.mangaailand

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class MangaAiLand : ZeistManga("Manga Ai Land", "https://manga-ai-land.blogspot.com", "ar") {

    override val hasFilters = true
    override val hasLanguageFilter = false

    override val chapterCategory = "فصل"

    override fun getGenreList(): List<Genre> = listOf(
        Genre("تراجيدي", "تراجيدي"),
        Genre("تاريخي", "تاريخي"),
        Genre("أكشن", "أكشن"),
        Genre("خيالي", "خيالي"),
        Genre("جيشي", "جيشي"),
        Genre("تشويق", "تشويق"),
        Genre("سينين", "سينين"),
        Genre("سحري", "سحري"),
        Genre("دراما", "دراما"),
        Genre("عصابات", "عصابات"),
        Genre("عسكري", "عسكري"),
        Genre("شونين", "شونين"),
        Genre("مغامرة", "مغامرة"),
        Genre("فنون قتالية", "فنون قتالية"),
        Genre("غموض", "غموض"),
        Genre("وحوش", "وحوش"),
        Genre("نجاة", "نجاة"),
        Genre("نفسي", "نفسي"),
    )
}
