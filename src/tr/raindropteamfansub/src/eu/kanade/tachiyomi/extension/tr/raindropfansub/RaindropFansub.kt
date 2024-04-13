package eu.kanade.tachiyomi.extension.tr.raindropfansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class RaindropFansub : MangaThemesia(
    "Raindrop Fansub",
    "https://www.raindropteamfan.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyy", Locale("tr")),
)
