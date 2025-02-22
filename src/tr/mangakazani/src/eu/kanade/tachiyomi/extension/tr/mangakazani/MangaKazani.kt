package eu.kanade.tachiyomi.extension.tr.mangakazani

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class MangaKazani : MangaThemesia(
    lang = "tr",
    baseUrl = "https://mangakazani.com",
    name = "MangaKazani",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
    mangaUrlDirectory = "/seriler",
)
