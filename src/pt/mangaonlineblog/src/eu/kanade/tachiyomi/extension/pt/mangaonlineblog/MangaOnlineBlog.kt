package eu.kanade.tachiyomi.extension.pt.mangaonlineblog

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOnlineBlog : Madara(
    "Manga Online Blog",
    "https://mangaonline.blog",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
