package eu.kanade.tachiyomi.extension.th.lamimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LamiManga : MangaThemesia(
    "Lami-Manga",
    "https://www.lami-manga.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
