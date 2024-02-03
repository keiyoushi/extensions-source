package eu.kanade.tachiyomi.extension.zh.sixmh

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SixMH : MCCMS("六漫画", "https://www.liumanhua.com") {

    override val versionId get() = 2

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Delete old preferences for "6漫画/zh/1"
            Injekt.get<Application>().deleteSharedPreferences("source_7259486566651312186")
        }
    }

    override fun getMangaUrl(manga: SManga) = "https://m.liumanhua.com" + manga.url
    override fun getChapterUrl(chapter: SChapter) = "https://m.liumanhua.com" + chapter.url
}
