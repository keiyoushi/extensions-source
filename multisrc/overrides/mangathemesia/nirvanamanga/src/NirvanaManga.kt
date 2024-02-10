package eu.kanade.tachiyomi.extension.tr.nirvanamanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NirvanaManga : MangaThemesia(
    "Nirvana Manga",
    "https://nirvanamanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
)
