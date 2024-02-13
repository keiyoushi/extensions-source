package eu.kanade.tachiyomi.extension.id.comicaso

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Comicaso : MangaThemesia(
    "Comicaso",
    "https://comicaso.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
)
