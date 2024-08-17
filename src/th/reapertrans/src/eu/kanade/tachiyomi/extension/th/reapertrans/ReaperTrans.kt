package eu.kanade.tachiyomi.extension.th.reapertrans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ReaperTrans : MangaThemesia(
    "ReaperTrans",
    "https://www.reapertrans.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
