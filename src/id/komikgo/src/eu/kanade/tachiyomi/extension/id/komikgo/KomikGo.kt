package eu.kanade.tachiyomi.extension.id.komikgo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KomikGo : MangaThemesia(
    "KomikGO",
    "https://komikgo.xyz",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
)
