package eu.kanade.tachiyomi.extension.tr.turktoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class TurkToon : MangaThemesia(
    "TurkToon",
    "https://turktoon.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")),
)
