package eu.kanade.tachiyomi.extension.ar.mangatak

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTak : MangaThemesia(
    "MangaTak",
    "https://mangatak.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM DD, yyyy", Locale("ar")),
)
