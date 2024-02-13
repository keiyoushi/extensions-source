package eu.kanade.tachiyomi.extension.es.shadowmangas

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ShadowMangas : MangaThemesia(
    "Shadow Mangas",
    "https://shadowmangas.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
)
