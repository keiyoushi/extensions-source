package eu.kanade.tachiyomi.extension.en.kirascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import rx.Observable
import rx.schedulers.Schedulers

class KiraScans : MangaThemesia(
    "Kira Scans",
    "https://kirascans.com",
    "en",
) {
    private val lockedChapterSelector = "p.fw-semibold:contains(This chapter is locked)"

    // Perform asynchronous checks for paid chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .flatMap { allChapters ->
                if (allChapters.isEmpty()) {
                    return@flatMap Observable.just(emptyList<SChapter>())
                }
                val chaptersToCheckCount = 20
                val latestChapters = allChapters.take(chaptersToCheckCount)
                val olderChapters = allChapters.drop(chaptersToCheckCount)
                val chapterChecks = latestChapters.map { chapter ->
                    Observable.fromCallable { isChapterFree(chapter) }
                        .subscribeOn(Schedulers.io())
                }
                Observable.zip(chapterChecks) { results ->
                    val freeLatestChapters = mutableListOf<SChapter>()
                    results.forEachIndexed { index, isFree ->
                        if (isFree as Boolean) {
                            freeLatestChapters.add(latestChapters[index])
                        }
                    }
                    freeLatestChapters + olderChapters
                }
            }
    }

    private fun isChapterFree(chapter: SChapter): Boolean {
        val maxRetries = 3
        for (attempt in 1..maxRetries) {
            try {
                val chapterResponse = client.newCall(GET(baseUrl + chapter.url, headers)).execute()
                val document = chapterResponse.asJsoup()
                return document.selectFirst(lockedChapterSelector) == null
            } catch (e: Exception) {}
        }
        return true
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response)
    }
}
