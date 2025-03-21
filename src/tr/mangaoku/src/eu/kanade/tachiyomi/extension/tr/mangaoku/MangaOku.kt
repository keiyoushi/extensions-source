package eu.kanade.tachiyomi.extension.tr.mangaoku

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOku : MangaThemesia(
    "Manga Oku",
    "https://mangaoku.org.tr",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
)
