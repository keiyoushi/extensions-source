package eu.kanade.tachiyomi.extension.tr.mangasiginagi

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSiginagi : MangaThemesia(
    "Manga Siginagi",
    "https://mangasiginagi.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
)
