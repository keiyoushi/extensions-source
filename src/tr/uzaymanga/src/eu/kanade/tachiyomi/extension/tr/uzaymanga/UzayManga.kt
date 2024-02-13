package eu.kanade.tachiyomi.extension.tr.uzaymanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class UzayManga : MangaThemesia(
    "Uzay Manga",
    "https://uzaymanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")),
)
