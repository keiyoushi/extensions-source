package eu.kanade.tachiyomi.extension.pt.huntersscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuntersScans : Madara(
    "Hunters Scan",
    "https://readhunters.xyz",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .readTimeout(3, TimeUnit.MINUTES)
        .build()

    override val mangaSubString = "comics"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Observable.fromCallable { fetchAllChapters(manga) }

    private fun fetchAllChapters(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val document = client.newCall(POST("${getMangaUrl(manga)}ajax/chapters?t=${page++}", xhrHeaders))
                .execute()
                .asJsoup()
            val currentPage = document.select(chapterListSelector())
                .map(::chapterFromElement)

            chapters += currentPage

            if (currentPage.isEmpty()) {
                return chapters
            }
        }
    }
}
