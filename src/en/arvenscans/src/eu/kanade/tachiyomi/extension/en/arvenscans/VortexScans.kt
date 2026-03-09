package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

class VortexScans :
    Iken(
        "Vortex Scans",
        "en",
        "https://vortexscans.org",
        "https://api.vortexscans.org",
    ) {

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservable()
        .map(::chapterListParse)
}
