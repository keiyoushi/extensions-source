package eu.kanade.tachiyomi.extension.tr.adumanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class AduManga : MangaThemesia(
    "Adu Manga",
    "https://adumanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en")),
)
