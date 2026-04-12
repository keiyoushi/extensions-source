package eu.kanade.tachiyomi.extension.id.komikdewasaart

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class KomikDewasaArt :
    MangaThemesia(
        "Komik Dewasa Art",
        "https://komikdewasa.art",
        "id",
        mangaUrlDirectory = "/komik",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    )
