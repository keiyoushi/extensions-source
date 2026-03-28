package eu.kanade.tachiyomi.extension.tr.amangaplanet

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class AmangaPlanet :
    MangaThemesia(
        name = "Amanga Planet",
        baseUrl = "https://www.amangaplanet.com.tr",
        lang = "tr",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    )
