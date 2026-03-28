package eu.kanade.tachiyomi.extension.en.mangatrend

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTrend :
    MangaThemesia(
        "Manga Trend",
        "https://mangatrend.org",
        "en",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    )
