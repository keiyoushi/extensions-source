package eu.kanade.tachiyomi.extension.tr.mangakoleji

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKoleji : MangaThemesia(
    "Manga Koleji",
    "https://mangakoleji.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
)
