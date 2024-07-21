package eu.kanade.tachiyomi.extension.ar.noonscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class NoonScan : MangaThemesia(
    "نون سكان",
    "https://noonscan.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
)
