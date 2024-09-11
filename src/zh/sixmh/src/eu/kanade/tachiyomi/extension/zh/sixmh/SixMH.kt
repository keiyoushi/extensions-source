package eu.kanade.tachiyomi.extension.zh.sixmh

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SixMH : MCCMS("六漫画", "https://www.liumanhua.com") {
    private val dataRegex = Regex("var DATA = '([A-Za-z0-9+/=]+)'")
    private val json by injectLazy<Json>()
    override val versionId get() = 2

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Delete old preferences for "6漫画/zh/1"
            Injekt.get<Application>().deleteSharedPreferences("source_7259486566651312186")
        }
    }

    override fun getMangaUrl(manga: SManga) = "https://m.liumanhua.com" + manga.url
    override fun getChapterUrl(chapter: SChapter) = "https://m.liumanhua.com" + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val encodedData = dataRegex.find(response.body.string())?.groupValues?.get(1) ?: ""
        val cid = response.request.url.pathSegments.last().removeSuffix(".html").toIntOrNull() ?: 0
        val decodedData = decodeData(encodedData, cid)
        val images = json.decodeFromString<List<Image>>(decodedData)
        return images.mapIndexed { index, image -> Page(index, imageUrl = image.url) }
    }
}
