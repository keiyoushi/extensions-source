package eu.kanade.tachiyomi.extension.tr.merlinshoujo

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MerlinShoujo : MangaThemesia(
    "Merlin Shoujo",
    "https://merlinshoujo.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
)
