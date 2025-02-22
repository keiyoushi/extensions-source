package eu.kanade.tachiyomi.extension.tr.ayatoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Ayatoon : MangaThemesia(
    "Ayatoon",
    "https://ayatoon.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
)
