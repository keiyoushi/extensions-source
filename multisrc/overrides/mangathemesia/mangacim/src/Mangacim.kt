package eu.kanade.tachiyomi.extension.tr.mangacim

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Mangacim : MangaThemesia(
    "Mangacim",
    "https://www.mangacim.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr")),
)
