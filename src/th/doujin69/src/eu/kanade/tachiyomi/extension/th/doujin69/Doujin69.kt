package eu.kanade.tachiyomi.extension.th.doujin69

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Doujin69 : MangaThemesia(
    "Doujin69",
    "https://doujin69.com",
    "th",
    mangaUrlDirectory = "/doujin",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")),
)
