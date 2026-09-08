package eu.kanade.tachiyomi.extension.ar.mangaailand

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source

@Source
abstract class MangaAiLand : ZeistManga() {

    override val hasFilters = true
    override val hasLanguageFilter = false

    override val chapterCategory = "فصل"

    override fun getGenreList() = listOf(
        "تراجيدي", "تاريخي", "أكشن", "خيالي",
        "جيشي", "تشويق", "سينين", "سحري",
        "دراما", "عصابات", "عسكري", "شونين",
        "مغامرة", "فنون قتالية", "غموض", "وحوش",
        "نجاة", "نفسي",
    ).map { Genre(it, it) }
}
