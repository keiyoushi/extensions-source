package eu.kanade.tachiyomi.extension.tr.gaiatoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Gaiatoon : MangaThemesia(
    "Gaiatoon",
    "https://gaiatoon.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
)
