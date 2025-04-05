package eu.kanade.tachiyomi.extension.th.mangastep

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Mangastep : MangaThemesia(
    "Mangastep",
    "https://mangastep.com",
    "th",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
