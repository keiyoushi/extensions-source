package eu.kanade.tachiyomi.extension.en.flonescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class FloneScans : MangaThemesia(
    "Flone Scans",
    "https://sweetmanhwa.online",
    "en",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
)
