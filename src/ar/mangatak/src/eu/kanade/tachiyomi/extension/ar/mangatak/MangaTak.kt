package eu.kanade.tachiyomi.extension.ar.mangatak

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesiaAlt
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTak : MangaThemesiaAlt(
    "MangaTak",
    "https://mangatak.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM DD, yyyy", Locale("ar")),
)
