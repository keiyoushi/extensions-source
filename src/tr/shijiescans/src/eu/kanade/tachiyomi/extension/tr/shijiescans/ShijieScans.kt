package eu.kanade.tachiyomi.extension.tr.shijiescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class ShijieScans : MangaThemesia(
    "Shijie Scans",
    "https://shijiescans.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr")),
)
