package eu.kanade.tachiyomi.extension.zh.gufengmh

import eu.kanade.tachiyomi.multisrc.sinmh.ProgressiveParser
import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import rx.Observable
import rx.Single

class Gufengmh : SinMH("古风漫画网", "https://www.gufengmh.com") {

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            if (status == SManga.COMPLETED) return@apply
            val firstChapter = document.selectFirst(".chapter-body li > a") ?: return@apply
            if (firstChapter.attr("href").startsWith("javascript")) {
                status = SManga.LICENSED
            }
        }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        Single.create<List<SChapter>> { subscriber ->
            val pcResponse = client.newCall(GET(baseUrl + manga.url, headers)).execute()
            val pcResult = chapterListParse(pcResponse, ".chapter-body li > a", "span.sj")
            if (pcResult.none { it.url.isEmpty() }) return@create subscriber.onSuccess(pcResult)
            // Example: https://www.gufengmh9.com/manhua/niaoling/
            val mobileResponse = client.newCall(GET(mobileUrl + manga.url, headers)).execute()
            val mobileResult = chapterListParse(mobileResponse, ".list li > a", ".pic_zi:nth-of-type(4) > dd")
            val pcAscending = pcResult.asReversed()
            val mobileAscending = mobileResult.asReversed()
            for ((pcChapter, mobileChapter) in pcAscending zip mobileAscending) {
                if (pcChapter.name != mobileChapter.name) return@create subscriber.onSuccess(mobileResult)
                pcChapter.url = mobileChapter.url
            }
            pcAscending.forEachIndexed { i, chapter ->
                if (chapter.url.isNotEmpty()) return@forEachIndexed
                if (i == 0) return@create subscriber.onSuccess(mobileResult)
                val prevUrl = pcAscending[i - 1].url
                val response = client.newCall(GET(baseUrl + prevUrl, headers)).execute()
                chapter.url = buildString {
                    append(prevUrl, 0, prevUrl.lastIndexOf('/') + 1)
                    append(ProgressiveParser(response.body.string()).substringBetween("""nextChapterData = {"id":""", ","))
                    append(".html")
                }
            }
            subscriber.onSuccess(pcResult)
        }.toObservable()
}
