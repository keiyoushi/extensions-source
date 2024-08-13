package eu.kanade.tachiyomi.extension.th.popsmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class PopsManga : MangaThemesia(
    "PopsManga",
    "https://www.popsmanga.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
