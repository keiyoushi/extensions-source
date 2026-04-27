package eu.kanade.tachiyomi.extension.es.lunapieces

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class LunaPieces :
    MangaThemesia(
        "Luna Pieces",
        "https://lunapiecesfansub.com",
        "es",
        mangaUrlDirectory = "/doujinshi",
        dateFormat = SimpleDateFormat("d MMMM, yyyy", Locale("es")),
    )
