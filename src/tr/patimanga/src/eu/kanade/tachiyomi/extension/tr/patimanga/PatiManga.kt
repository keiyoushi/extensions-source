package eu.kanade.tachiyomi.extension.tr.patimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class PatiManga : MangaThemesia(
    "Pati Manga",
    "https://www.patimanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
)
