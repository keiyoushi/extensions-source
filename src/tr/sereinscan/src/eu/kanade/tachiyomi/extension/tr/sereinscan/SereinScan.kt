package eu.kanade.tachiyomi.extension.tr.sereinscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class SereinScan : MangaThemesia(
    "Serein Scan",
    "https://sereinscan.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr")),
)
