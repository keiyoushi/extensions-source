package eu.kanade.tachiyomi.extension.pt.mangaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOnline :
    Madara(
        "Manga Online",
        "https://mangasonline.blog",
        "pt-BR",
        SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
