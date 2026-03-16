package eu.kanade.tachiyomi.extension.tr.koreliscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KoreliScans :
    MangaThemesia(
        "Koreli Scans",
        "https://nabimanga.com",
        "tr",
        dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
    )
